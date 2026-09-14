package com.quangkhai.vehicletracking_backend.simulation.service;

import com.quangkhai.vehicletracking_backend.simulation.dto.*;
import com.quangkhai.vehicletracking_backend.simulation.entity.*;
import com.quangkhai.vehicletracking_backend.simulation.motion.RouteMotion;
import com.quangkhai.vehicletracking_backend.simulation.repository.SimulationRepository;
import com.quangkhai.vehicletracking_backend.trip.dto.TripCreateRequest;
import com.quangkhai.vehicletracking_backend.trip.entity.*;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.trip.service.TripService;
import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
import com.quangkhai.vehicletracking_backend.telemetry.dto.TelemetryRequest;
import com.quangkhai.vehicletracking_backend.telemetry.entity.TelemetrySource;
import com.quangkhai.vehicletracking_backend.telemetry.repository.*;
import com.quangkhai.vehicletracking_backend.telemetry.service.TelemetryService;
import com.quangkhai.vehicletracking_backend.vehicle.repository.VehicleRepository;
import com.quangkhai.vehicletracking_backend.traffic.TrafficSource;
import com.quangkhai.vehicletracking_backend.traffic.TrafficStatus;
import com.quangkhai.vehicletracking_backend.traffic.eta.TripEtaResponse;
import com.quangkhai.vehicletracking_backend.traffic.eta.TrafficEtaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static org.springframework.http.HttpStatus.*;

@Service @RequiredArgsConstructor
public class SimulationService {
    private final SimulationRepository runs;
    private final TripRepository trips;
    private final VehicleRepository vehicles;
    private final TripService tripService;
    private final TelemetryService telemetry;
    private final TelemetryRepository samples;
    private final VehiclePositionRepository positions;
    private final Clock operationsClock;
    private final TrafficEtaService trafficEta;
    // Routes are immutable. Bound the decoded geometry cache for repeated scheduler/SSE reads.
    private final Map<Long,RouteMotion> motions=Collections.synchronizedMap(new LinkedHashMap<>(16,0.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long,RouteMotion> eldest) { return size()>100; }
    });

    @Transactional
    public SimulationResponse play(long tripId) {
        var trip=lockTrip(tripId);
        var run=runs.findByTripId(tripId).orElse(null);
        if(run!=null && run.getStatus()==SimulationStatus.RUNNING && trip.getStatus()==TripStatus.IN_PROGRESS) return describe(trip,run);
        if(run!=null && run.getStatus()!=SimulationStatus.PAUSED) throw conflict("Phiên đã kết thúc. Dùng Chạy lại để tạo chuyến mới.");
        if(trip.getStatus()!=TripStatus.SCHEDULED && trip.getStatus()!=TripStatus.IN_PROGRESS) throw conflict("Chuyến đã kết thúc.");
        if(samples.existsByTripIdAndSource(tripId,TelemetrySource.GPS)) throw conflict("Chuyến đã nhận GPS; hãy tạo chuyến khác để mô phỏng.");
        motion(trip); // Validate before modifying trip lifecycle.
        tripService.start(tripId);
        var now=now();
        if(run==null) run=runs.saveAndFlush(new SimulationRunEntity(tripId,now));
        run.changeStatus(SimulationStatus.RUNNING,now);
        emit(trip,run,false);
        return describe(trip,run);
    }
    @Transactional
    public SimulationResponse pause(long tripId) {
        var trip=lockTrip(tripId); var run=requireRun(tripId);
        if(run.getStatus()==SimulationStatus.PAUSED) return describe(trip,run);
        if(run.getStatus()!=SimulationStatus.RUNNING) throw conflict("Phiên không đang chạy.");
        advance(trip,run,now());
        if(run.getStatus()==SimulationStatus.RUNNING) { run.changeStatus(SimulationStatus.PAUSED,now()); emit(trip,run,true); }
        return describe(trip,run);
    }
    @Transactional
    public SimulationResponse speed(long tripId,int multiplier) {
        if(multiplier!=1 && multiplier!=5 && multiplier!=10) throw new ResponseStatusException(BAD_REQUEST,"Chỉ hỗ trợ 1×, 5× hoặc 10×.");
        var trip=lockTrip(tripId); var run=requireRun(tripId);
        if(run.getStatus()!=SimulationStatus.RUNNING && run.getStatus()!=SimulationStatus.PAUSED) throw conflict("Phiên đã kết thúc.");
        var now=now(); advance(trip,run,now);
        run.changeMultiplier(multiplier,now);
        return describe(trip,run);
    }
    @Transactional
    public SimulationResponse stop(long tripId) {
        var trip=lockTrip(tripId); var run=requireRun(tripId);
        stop(trip,run);
        return describe(trip,run);
    }
    @Transactional
    public SimulationResponse reset(long tripId) {
        var trip=lockTrip(tripId); var run=requireRun(tripId);
        if(run.getReplacementTripId()!=null) {
            var replacement=trips.findById(run.getReplacementTripId()).orElseThrow();
            return describe(replacement,requireRun(replacement.getId()));
        }
        motion(trip);
        stop(trip,run);
        var replacement=tripService.create(new TripCreateRequest(trip.getVehicle().getId(),trip.getRoute().getId(),now()));
        var next=runs.saveAndFlush(new SimulationRunEntity(replacement.trip().id(),now()));
        run.replaceWith(next.getTripId(),now());
        return describe(trips.findById(next.getTripId()).orElseThrow(),next);
    }
    @Transactional
    public void tick(long tripId) {
        var trip=lockTrip(tripId); var run=requireRun(tripId);
        advance(trip,run,now());
    }
    @Transactional
    public void recover(long tripId) {
        var trip=lockTrip(tripId); var run=requireRun(tripId);
        if(run.getStatus()!=SimulationStatus.RUNNING) return;
        if(trip.getStatus()==TripStatus.IN_PROGRESS) { run.changeStatus(SimulationStatus.PAUSED,now()); emit(trip,run,true); }
        else run.changeStatus(trip.getStatus()==TripStatus.COMPLETED?SimulationStatus.COMPLETED:SimulationStatus.STOPPED,now());
    }
    @Transactional
    public void fail(long tripId) {
        var trip=lockTrip(tripId); var run=requireRun(tripId);
        if(run.getStatus()==SimulationStatus.RUNNING || run.getStatus()==SimulationStatus.PAUSED)
            run.fail("Mô phỏng gặp lỗi. Dừng phiên hoặc tạo chuyến mới sau khi kiểm tra tuyến.",now());
    }
    @Transactional(readOnly=true)
    public List<Long> activeTripIds() {
        return runs.findByStatusIn(List.of(SimulationStatus.RUNNING,SimulationStatus.PAUSED)).stream().map(SimulationRunEntity::getTripId).toList();
    }
    private void advance(TripEntity trip,SimulationRunEntity run,Instant now) {
        if(run.getStatus()!=SimulationStatus.RUNNING && run.getStatus()!=SimulationStatus.PAUSED) return;
        if(trip.getStatus()==TripStatus.COMPLETED || trip.getStatus()==TripStatus.CANCELLED) {
            run.changeStatus(trip.getStatus()==TripStatus.COMPLETED?SimulationStatus.COMPLETED:SimulationStatus.STOPPED,now); return;
        }
        if(run.getStatus()!=SimulationStatus.RUNNING) return;
        double delta=Math.max(0,Duration.between(run.getLastTickAt(),now).toNanos()/1_000_000_000d);
        if(delta==0) return;
        var motion=motion(trip);
        double baselineRemaining = Math.max(0, motion.duration() - run.getElapsedSeconds());
        double trafficRate = trafficEta.simulationRate(trip.getId(), baselineRemaining);
        double progressDelta = delta * run.getMultiplier() * trafficRate;
        run.advance(Math.min(motion.duration(),run.getElapsedSeconds()+progressDelta),now);
        emit(trip,run,false,trafficRate);
        if(run.getElapsedSeconds()>=motion.duration()) { tripService.complete(trip.getId()); run.changeStatus(SimulationStatus.COMPLETED,now); }
    }
    private void stop(TripEntity trip,SimulationRunEntity run) {
        if(trip.getStatus()==TripStatus.SCHEDULED || trip.getStatus()==TripStatus.IN_PROGRESS) {
            if(trip.getStatus()==TripStatus.IN_PROGRESS && run.getStatus()!=SimulationStatus.FAILED) emit(trip,run,true);
            tripService.cancel(trip.getId());
        }
        if(run.getStatus()!=SimulationStatus.COMPLETED && run.getStatus()!=SimulationStatus.STOPPED) run.changeStatus(SimulationStatus.STOPPED,now());
    }
    private void emit(TripEntity trip,SimulationRunEntity run,boolean stationary) {
        double baselineRemaining = Math.max(0, motion(trip).duration() - run.getElapsedSeconds());
        double trafficRate = stationary ? 0d : trafficEta.simulationRate(trip.getId(), baselineRemaining);
        emit(trip, run, stationary, trafficRate);
    }
    private void emit(TripEntity trip,SimulationRunEntity run,boolean stationary,double trafficRate) {
        var frame=motion(trip).at(run.getElapsedSeconds());
        if (!stationary && frame.speedKmh() > 0 && Double.isFinite(trafficRate)) {
            frame = new RouteMotion.Frame(frame.latitude(), frame.longitude(), frame.heading(),
                frame.speedKmh() * Math.max(0, Math.min(1.5d, trafficRate)), frame.progressPercent(),
                frame.nextStopSequence(), frame.nextStopEtaSeconds(), frame.dwelling(), frame.finished());
        }
        Instant recorded=now();
        // TripService uses the application wall clock for lifecycle changes;
        // keep the first simulator sample from being timestamped just before
        // startedAt when a deterministic/test clock is a few microseconds
        // behind it.
        if (trip.getStartedAt()!=null && recorded.isBefore(trip.getStartedAt())) recorded=trip.getStartedAt();
        var latest=positions.findById(trip.getVehicle().getId());
        if(latest.isPresent() && !recorded.isAfter(latest.get().getSample().getRecordedAt()))
            recorded=latest.get().getSample().getRecordedAt().plus(1,ChronoUnit.MICROS);
        telemetry.ingestSimulator(new TelemetryRequest(UUID.randomUUID(),trip.getVehicle().getId(),trip.getId(),recorded,
            frame.latitude(),frame.longitude(),stationary?0:frame.speedKmh(),frame.heading(),0d,TelemetrySource.SIMULATOR),
            simulatedAt(trip,run));
    }
    public SimulationResponse describe(TripEntity trip,SimulationRunEntity run) {
        RouteMotion.Frame frame=null;
        if(run.getStatus()!=SimulationStatus.FAILED) {
            try { frame=motion(trip).at(run.getElapsedSeconds()); }
            catch(ResponseStatusException ignored) { /* Historical malformed geometry remains inspectable. */ }
        }
        if (frame != null && run.getStatus() == SimulationStatus.RUNNING) {
            double baselineRemaining = Math.max(0, motion(trip).duration() - run.getElapsedSeconds());
            double trafficRate = trafficEta.simulationRate(trip.getId(), baselineRemaining);
            if (frame.speedKmh() > 0 && Double.isFinite(trafficRate)) {
                frame = new RouteMotion.Frame(frame.latitude(), frame.longitude(), frame.heading(),
                    frame.speedKmh() * Math.max(0, Math.min(1.5d, trafficRate)), frame.progressPercent(),
                    frame.nextStopSequence(), frame.nextStopEtaSeconds(), frame.dwelling(), frame.finished());
            }
        }
        if(frame!=null && run.getStatus()!=SimulationStatus.RUNNING)
            frame=new RouteMotion.Frame(frame.latitude(),frame.longitude(),frame.heading(),0,frame.progressPercent(),
                frame.nextStopSequence(),frame.nextStopEtaSeconds(),frame.dwelling(),frame.finished());
        return new SimulationResponse(run.getId(),trip.getId(),run.getStatus(),run.getMultiplier(),run.getElapsedSeconds(),
            trip.getRoute().getEstimatedTripDurationSeconds(),simulatedAt(trip,run),run.getUpdatedAt(),run.getErrorMessage(),run.getReplacementTripId(),frame,
            trafficMetadata(trip));
    }
    private SimulationTrafficMetadata trafficMetadata(TripEntity trip) {
        try {
            TripEtaResponse eta = trafficEta.calculate(trip.getId());
            Long nextEta = eta.nextStopSequence() == null ? null : eta.stops().stream()
                    .filter(stop -> stop.sequenceNumber() == eta.nextStopSequence())
                    .map(TripEtaResponse.EtaStop::etaSeconds)
                    .findFirst().orElse(null);
            return new SimulationTrafficMetadata(eta.source(), eta.status(), nextEta,
                    eta.trafficObservedAt(), eta.trafficFetchedAt(), eta.status() == TrafficStatus.BLOCKED, eta.warning());
        } catch (RuntimeException ignored) {
            // Traffic must never break simulator position/lifecycle updates.
            return new SimulationTrafficMetadata(TrafficSource.UNAVAILABLE, TrafficStatus.UNAVAILABLE,
                    null, null, null, false, "TRAFFIC_UNAVAILABLE");
        }
    }
    private RouteMotion motion(TripEntity trip) {
        try { return motions.computeIfAbsent(trip.getRoute().getId(),id->new RouteMotion(RouteDetailResponse.from(trip.getRoute()))); }
        catch(IllegalArgumentException|ArithmeticException ex) { throw conflict("Geometry hoặc thời lượng tuyến không hợp lệ để mô phỏng. Hãy tính lại tuyến."); }
    }
    private Instant simulatedAt(TripEntity trip,SimulationRunEntity run) { return trip.getScheduledDepartureAt().plusMillis((long)(run.getElapsedSeconds()*1000)); }
    private TripEntity lockTrip(long id) {
        var trip=trips.findLockedById(id).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Không tìm thấy chuyến."));
        vehicles.findLockedById(trip.getVehicle().getId()).orElseThrow();
        return trip;
    }
    private SimulationRunEntity requireRun(long tripId) { return runs.findByTripId(tripId).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Chưa có phiên mô phỏng.")); }
    private Instant now() { return operationsClock.instant().truncatedTo(ChronoUnit.MICROS); }
    private ResponseStatusException conflict(String text) { return new ResponseStatusException(CONFLICT,text); }
}
