package com.quangkhai.vehicletracking_backend.simulation.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.quangkhai.vehicletracking_backend.checkin.repository.TripCheckInStateRepository;
import com.quangkhai.vehicletracking_backend.reroute.entity.RouteRevisionStatus;
import com.quangkhai.vehicletracking_backend.reroute.entity.NotificationSeverity;
import com.quangkhai.vehicletracking_backend.reroute.entity.NotificationType;
import com.quangkhai.vehicletracking_backend.reroute.entity.TripNotificationEntity;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripNotificationRepository;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripRouteRevisionRepository;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripTrafficAlertStateRepository;
import com.quangkhai.vehicletracking_backend.simulation.dto.SimulationAttemptResponse;
import com.quangkhai.vehicletracking_backend.simulation.dto.SimulationIncidentCreateRequest;
import com.quangkhai.vehicletracking_backend.simulation.dto.SimulationIncidentResponse;
import com.quangkhai.vehicletracking_backend.simulation.dto.SimulationResponse;
import com.quangkhai.vehicletracking_backend.simulation.dto.SimulationTrafficMetadata;
import com.quangkhai.vehicletracking_backend.simulation.entity.SimulationAttemptEntity;
import com.quangkhai.vehicletracking_backend.simulation.entity.SimulationIncidentEntity;
import com.quangkhai.vehicletracking_backend.simulation.entity.SimulationIncidentStatus;
import com.quangkhai.vehicletracking_backend.simulation.entity.SimulationAttemptMetadata;
import com.quangkhai.vehicletracking_backend.simulation.entity.SimulationRunEntity;
import com.quangkhai.vehicletracking_backend.simulation.entity.SimulationScenario;
import com.quangkhai.vehicletracking_backend.simulation.entity.SimulationStatus;
import com.quangkhai.vehicletracking_backend.simulation.motion.RouteMotion;
import com.quangkhai.vehicletracking_backend.simulation.repository.SimulationAttemptRepository;
import com.quangkhai.vehicletracking_backend.simulation.repository.SimulationIncidentRepository;
import com.quangkhai.vehicletracking_backend.simulation.repository.SimulationRepository;
import com.quangkhai.vehicletracking_backend.telemetry.dto.TelemetryRequest;
import com.quangkhai.vehicletracking_backend.telemetry.entity.TelemetrySource;
import com.quangkhai.vehicletracking_backend.telemetry.repository.TelemetryRepository;
import com.quangkhai.vehicletracking_backend.telemetry.repository.VehiclePositionRepository;
import com.quangkhai.vehicletracking_backend.telemetry.service.TelemetryService;
import com.quangkhai.vehicletracking_backend.traffic.TrafficSource;
import com.quangkhai.vehicletracking_backend.traffic.TrafficStatus;
import com.quangkhai.vehicletracking_backend.traffic.eta.TrafficEtaService;
import com.quangkhai.vehicletracking_backend.traffic.eta.TripEtaResponse;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.trip.service.TripService;
import com.quangkhai.vehicletracking_backend.vehicle.repository.VehicleRepository;

import lombok.RequiredArgsConstructor;

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
    private final SimulationAttemptRepository attempts;
    private final TripCheckInStateRepository checkInStates;
    private final TripTrafficAlertStateRepository alertStates;
    private final TripRouteRevisionRepository revisions;
    private final com.quangkhai.vehicletracking_backend.reroute.service.TripRouteGeometryService geometry;
    private final com.quangkhai.vehicletracking_backend.reroute.service.OffRouteEvaluationService offRoutes;
    private final SimulationIncidentRepository incidents;
    private final TripNotificationRepository notifications;

    @Transactional
    public SimulationIncidentResponse reportIncident(long tripId, long reporterDriverId,
                                                       SimulationIncidentCreateRequest request) {
        var trip = lockTrip(tripId);
        if (trip.getDriver() == null || trip.getDriver().getId() != reporterDriverId)
            throw new ResponseStatusException(NOT_FOUND, "Không tìm thấy chuyến được phân công.");
        var prior = incidents.findByIdempotencyKey(request.idempotencyKey()).orElse(null);
        if (prior != null) {
            if (prior.getTrip().getId() != tripId
                    || prior.getReportedByDriver() == null
                    || prior.getReportedByDriver().getId() != reporterDriverId)
                throw conflict("Khóa yêu cầu đã được dùng cho sự cố khác.");
            var currentRun = runs.findByTripId(tripId).orElse(null);
            var simulation = currentRun != null && trip.getAttemptNumber() == prior.getAttemptNumber()
                    ? describe(trip, currentRun) : null;
            return SimulationIncidentResponse.from(prior, simulation);
        }
        var run = requireRun(tripId);
        if (request.attemptNumber() != trip.getAttemptNumber()) throw conflict("Lượt chạy đã thay đổi. Hãy tải lại mô phỏng.");
        if (samples.existsByTripIdAndSource(tripId, TelemetrySource.GPS)) throw conflict("Chuyến đã nhận GPS; không thể ghi nhận sự cố mô phỏng.");
        if (trip.getStatus() != TripStatus.IN_PROGRESS
                || (run.getStatus() != SimulationStatus.RUNNING && run.getStatus() != SimulationStatus.PAUSED))
            throw conflict("Chỉ có thể ghi nhận sự cố cho chuyến mô phỏng đang hoạt động.");
        if (incidents.existsByTripIdAndAttemptNumberAndStatusIn(tripId, trip.getAttemptNumber(),
                List.of(SimulationIncidentStatus.OPEN, SimulationIncidentStatus.ACKNOWLEDGED)))
            throw conflict("Chuyến đang có sự cố chưa được xử lý.");

        var now = now();
        advance(trip, run, now);
        if (run.getStatus() == SimulationStatus.COMPLETED || trip.getStatus() != TripStatus.IN_PROGRESS)
            throw conflict("Chuyến đã kết thúc trước khi ghi nhận sự cố.");
        var frame = scenarioFrame(run, motion(trip).at(run.getElapsedSeconds()));
        var incident = incidents.saveAndFlush(new SimulationIncidentEntity(trip, trip.getDriver(), trip.getAttemptNumber(),
                request.type(), request.severity(), request.detail(), frame.latitude(), frame.longitude(),
                run.getElapsedSeconds(), request.idempotencyKey(), now));
        run.changeStatus(SimulationStatus.PAUSED, now);

        String title = request.type().label();
        String detail = incident.getDetail();
        String reason = detail == null ? title : (title + ": " + detail);
        if (reason.length() > 255) reason = reason.substring(0, 255);
        var notification = new TripNotificationEntity(trip, null, NotificationType.SIMULATION_INCIDENT,
                request.severity(), title, reason, request.idempotencyKey().toString(), "", null, null,
                "simulation-incident:" + request.idempotencyKey(), now);
        notification.attachSimulationIncident(incident);
        notifications.save(notification);
        emit(trip, run, true);
        return SimulationIncidentResponse.from(incident, describe(trip, run));
    }

    @Transactional
    public SimulationResponse play(long tripId) {
        var trip=lockTrip(tripId);
        var run=runs.findByTripId(tripId).orElse(null);
        if(run!=null && run.getStatus()==SimulationStatus.RUNNING && trip.getStatus()==TripStatus.IN_PROGRESS) return describe(trip,run);
        if(run!=null && run.getStatus()!=SimulationStatus.PAUSED) throw conflict("Phiên đã kết thúc. Dùng Chạy lại để bắt đầu lần mô phỏng mới.");
        if(trip.getStatus()!=TripStatus.SCHEDULED && trip.getStatus()!=TripStatus.IN_PROGRESS) throw conflict("Chuyến đã kết thúc.");
        if(samples.existsByTripIdAndSource(tripId,TelemetrySource.GPS)) throw conflict("Chuyến đã nhận GPS; hãy tạo chuyến khác để mô phỏng.");
        var baseline=motion(trip); // Validate before modifying trip lifecycle.
        boolean firstPlay=run==null || trip.getStartedAt()==null;
        tripService.start(tripId);
        var now=now();
        if(run==null) run=runs.saveAndFlush(new SimulationRunEntity(tripId,now));
        if(firstPlay) run.captureFirstPlay(SimulationAttemptMetadata.capture(trip,now,baseline.duration(),baseline.snapshot().totalDistanceMeters()));
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
        advance(trip,run,now());
        stop(trip,run);
        return describe(trip,run);
    }
    @Transactional
    public SimulationResponse reset(long tripId) {
        var trip=lockTrip(tripId); var run=requireRun(tripId);
        if (!trip.getVehicle().isActive()) throw conflict("Xe đã ngừng sử dụng.");
        if (samples.existsByTripIdAndSource(tripId,TelemetrySource.GPS)) throw conflict("Không thể chạy lại mô phỏng trên chuyến đã nhận GPS.");
        if (trips.findAllByVehicleIdOrderByScheduledDepartureAtDescIdDesc(trip.getVehicle().getId()).stream()
                .anyMatch(other -> !other.getId().equals(tripId) && other.getStatus()==TripStatus.IN_PROGRESS))
            throw conflict("Xe đang thực hiện chuyến khác. Hãy kết thúc chuyến đó trước.");
        motion(trip);
        var now=now();
        advance(trip,run,now);
        if (trip.getStatus()==TripStatus.SCHEDULED && run.getStatus()==SimulationStatus.PAUSED && run.getElapsedSeconds()==0) {
            // Keep the same clean attempt, but refresh its time anchor when an
            // operator comes back days later and chooses replay again.
            if (trip.getSchedule() == null) trip.reschedule(now);
            run.replay(now); trips.flush();
            return describe(trip,run);
        }
        attempts.saveAndFlush(new SimulationAttemptEntity(trip,run,now));
        trip.replay(now); run.replay(now);
        checkInStates.findById(tripId).ifPresent(state -> state.replay(trip.getAttemptNumber()));
        alertStates.findById(tripId).ifPresent(state -> state.replay(now));
        revisions.findTopByTripIdAndStatusOrderByRevisionNumberDesc(tripId,RouteRevisionStatus.ACTIVE)
            .ifPresent(revision -> revision.supersede(now));
        trips.flush();
        return describe(trip,run);
    }
    @Transactional(readOnly=true)
    public List<SimulationAttemptResponse> attempts(long tripId) {
        if (!trips.existsById(tripId)) throw new ResponseStatusException(NOT_FOUND,"Không tìm thấy chuyến.");
        return attempts.findAllByTripIdOrderByAttemptNumberDesc(tripId).stream().map(SimulationAttemptResponse::from).toList();
    }
    @Transactional
    public SimulationResponse scenario(long tripId, SimulationScenario scenario, int attemptNumber) {
        if (scenario==null || attemptNumber<1) throw new ResponseStatusException(BAD_REQUEST,"Kịch bản hoặc lượt chạy không hợp lệ.");
        var trip=lockTrip(tripId); var run=requireRun(tripId);
        if(trip.getAttemptNumber()!=attemptNumber) throw conflict("Lượt chạy đã thay đổi. Hãy tải lại mô phỏng.");
        if(samples.existsByTripIdAndSource(tripId,TelemetrySource.GPS)) throw conflict("Chuyến đã nhận GPS; không thể đổi kịch bản.");
        if(run.getStatus()!=SimulationStatus.RUNNING && run.getStatus()!=SimulationStatus.PAUSED) throw conflict("Phiên đã kết thúc.");
        advance(trip,run,now());
        if(run.getStatus()==SimulationStatus.COMPLETED) throw conflict("Phiên đã kết thúc.");
        if(run.getScenario()!=scenario) offRoutes.clearScenarioEpisode(tripId,now());
        run.changeScenario(scenario,now());
        if(trip.getStatus()==TripStatus.IN_PROGRESS) emit(trip,run,run.getStatus()!=SimulationStatus.RUNNING);
        if(scenario!=SimulationScenario.OFF_ROUTE && run.getStatus()==SimulationStatus.RUNNING
                && run.getElapsedSeconds()>=motion(trip).duration()) {
            tripService.complete(tripId); run.changeStatus(SimulationStatus.COMPLETED,now());
        }
        return describe(trip,run);
    }
    @Transactional
    public void tick(long tripId) {
        var trip=lockTrip(tripId); var run=requireRun(tripId);
        advance(trip,run,now());
    }
    @Transactional
    public void refreshRoute(long tripId) {
        var trip = lockTrip(tripId); var run = requireRun(tripId);
        if (trip.getStatus() != TripStatus.IN_PROGRESS || run.getStatus() != SimulationStatus.RUNNING)
            throw conflict("Chuyến không còn đang mô phỏng.");
        geometry.applyActive(trip, run.getElapsedSeconds());
        emit(trip, run, false);
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
        lockTrip(tripId); var run=requireRun(tripId);
        if(run.getStatus()==SimulationStatus.RUNNING || run.getStatus()==SimulationStatus.PAUSED)
            run.fail("Mô phỏng gặp lỗi. Kiểm tra tuyến rồi chọn Chạy lại.",now());
    }
    @Transactional(readOnly=true)
    public List<Long> activeTripIds() {
        return runs.findByStatusIn(List.of(SimulationStatus.RUNNING,SimulationStatus.PAUSED)).stream().map(run -> run.getTripId()).toList();
    }
    private void advance(TripEntity trip,SimulationRunEntity run,Instant now) {
        if(run.getStatus()!=SimulationStatus.RUNNING && run.getStatus()!=SimulationStatus.PAUSED) return;
        if(trip.getStatus()==TripStatus.COMPLETED || trip.getStatus()==TripStatus.CANCELLED) {
            run.changeStatus(trip.getStatus()==TripStatus.COMPLETED?SimulationStatus.COMPLETED:SimulationStatus.STOPPED,now); return;
        }
        if(run.getStatus()!=SimulationStatus.RUNNING) return;
        double delta=Math.max(0,Duration.between(run.getLastTickAt(),now).toNanos()/1_000_000_000d);
        if(delta==0) return;
        try { geometry.applyActive(trip,run.getElapsedSeconds()); }
        catch (IllegalArgumentException ex) { /* A disconnected replacement must not teleport or fail the running vehicle. */ }
        var motion=motion(trip);
        double budget=delta*run.getMultiplier(), elapsed=run.getElapsedSeconds(), consumed=0;
        double progressLimit=motion.duration();
        if(run.getScenario()==SimulationScenario.OFF_ROUTE) {
            int target=nextUnvisitedStop(trip,motion,elapsed);
            progressLimit=Math.max(elapsed,Math.min(progressLimit,motion.arrivalAt(target)));
        }
        double trafficRate=1;
        while(budget>1e-9 && elapsed<progressLimit) {
            trafficRate=rate(trip,run,motion,elapsed);
            if(trafficRate<=0) { consumed+=budget; budget=0; break; }
            double boundary=Math.min(progressLimit,motion.nextBoundary(elapsed));
            double used=Math.min(budget,(boundary-elapsed)/trafficRate);
            if(used<=0) break;
            elapsed=Math.min(boundary,elapsed+used*trafficRate); consumed+=used; budget-=used;
        }
        if(run.getScenario()==SimulationScenario.OFF_ROUTE) {
            consumed+=budget;
            if(elapsed>=progressLimit-1e-9) trafficRate=0;
        }
        run.addVirtualSeconds(consumed);
        run.advance(Math.min(motion.duration(),elapsed),now);
        emit(trip,run,false,trafficRate);
        if(run.getElapsedSeconds()>=motion.duration() && run.getScenario()!=SimulationScenario.OFF_ROUTE) { tripService.complete(trip.getId()); run.changeStatus(SimulationStatus.COMPLETED,now); }
    }
    private void stop(TripEntity trip,SimulationRunEntity run) {
        if(trip.getStatus()==TripStatus.SCHEDULED || trip.getStatus()==TripStatus.IN_PROGRESS) {
            if(trip.getStatus()==TripStatus.IN_PROGRESS && run.getStatus()!=SimulationStatus.FAILED) emit(trip,run,true);
            tripService.cancel(trip.getId());
        }
        if(run.getStatus()!=SimulationStatus.COMPLETED && run.getStatus()!=SimulationStatus.STOPPED) run.changeStatus(SimulationStatus.STOPPED,now());
    }
    private void emit(TripEntity trip,SimulationRunEntity run,boolean stationary) {
        double trafficRate = stationary ? 0d : rate(trip,run,motion(trip),run.getElapsedSeconds());
        emit(trip, run, stationary, trafficRate);
    }
    private void emit(TripEntity trip,SimulationRunEntity run,boolean stationary,double trafficRate) {
        var frame=scenarioFrame(run,motion(trip).at(run.getElapsedSeconds()));
        if (!stationary && frame.speedKmh() > 0 && Double.isFinite(trafficRate)) {
            frame = new RouteMotion.Frame(frame.latitude(), frame.longitude(), frame.heading(),
                frame.speedKmh() * Math.max(0, trafficRate), frame.progressPercent(),
                frame.nextStopSequence(), frame.nextStopEtaSeconds(), frame.dwellRemainingSeconds(),
                frame.dwelling(), frame.finished());
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
        return describe(trip, run, false);
    }

    /** Realtime snapshots must not wait for provider queries or route/traffic matching. */
    public SimulationResponse describeSnapshot(TripEntity trip, SimulationRunEntity run) {
        return describe(trip, run, true);
    }

    private SimulationResponse describe(TripEntity trip, SimulationRunEntity run, boolean snapshot) {
        RouteMotion.Frame frame=null;
        double duration=trip.getRoute().getEstimatedTripDurationSeconds();
        if(run.getStatus()!=SimulationStatus.FAILED) {
            try { var motion=motion(trip); duration=motion.duration(); frame=scenarioFrame(run,motion.at(run.getElapsedSeconds())); }
            catch(ResponseStatusException ignored) { /* Historical malformed geometry remains inspectable. */ }
        }
        if (frame != null && run.getStatus() == SimulationStatus.RUNNING && !snapshot) {
            double trafficRate = rate(trip,run,motion(trip),run.getElapsedSeconds());
            if (frame.speedKmh() > 0 && Double.isFinite(trafficRate)) {
                frame = new RouteMotion.Frame(frame.latitude(), frame.longitude(), frame.heading(),
                    frame.speedKmh() * Math.max(0, trafficRate), frame.progressPercent(),
                    frame.nextStopSequence(), frame.nextStopEtaSeconds(), frame.dwellRemainingSeconds(),
                    frame.dwelling(), frame.finished());
            }
        }
        if(frame!=null && run.getStatus()!=SimulationStatus.RUNNING)
            frame=new RouteMotion.Frame(frame.latitude(),frame.longitude(),frame.heading(),0,frame.progressPercent(),
                frame.nextStopSequence(),frame.nextStopEtaSeconds(),frame.dwellRemainingSeconds(),
                frame.dwelling(),frame.finished());
        if (snapshot && frame != null && run.getStatus() == SimulationStatus.RUNNING) {
            var sample = positions.findById(trip.getVehicle().getId()).map(p -> p.getSample()).orElse(null);
            double speed = sample != null && trip.getId().equals(sample.getTripId())
                    && sample.getAttemptNumber() == trip.getAttemptNumber() ? sample.getSpeedKmh() : 0;
            frame = new RouteMotion.Frame(frame.latitude(), frame.longitude(), frame.heading(), speed,
                    frame.progressPercent(), frame.nextStopSequence(), frame.nextStopEtaSeconds(),
                    frame.dwellRemainingSeconds(), frame.dwelling(), frame.finished());
        }
        return new SimulationResponse(run.getId(),trip.getId(),run.getStatus(),run.getMultiplier(),run.getElapsedSeconds(),
            duration,simulatedAt(trip,run),run.getUpdatedAt(),run.getErrorMessage(),run.getReplacementTripId(),frame,
            trafficMetadata(trip, snapshot),
            trip.getAttemptNumber(),frame==null?null:geometry.resolve(trip).revisionId(),run.getScenario(),run.getVirtualElapsedSeconds());
    }
    private double rate(TripEntity trip, SimulationRunEntity run, RouteMotion motion, double elapsed) {
        if(run.getScenario()==SimulationScenario.BLOCKED) return 0;
        if(run.getScenario()==SimulationScenario.OFF_ROUTE && elapsed>=motion.arrivalAt(nextUnvisitedStop(trip,motion,elapsed))-1e-9) return 0;
        if(motion.at(elapsed).dwelling()) return 1;
        return switch(run.getScenario()) {
            case NORMAL, OFF_ROUTE -> 1;
            case CONGESTION -> .5;
            case BLOCKED -> 0;
            case CURRENT_TRAFFIC -> trafficEta.cachedSimulationRate(trip.getId(),Math.max(0,motion.duration()-elapsed));
        };
    }
    private int nextUnvisitedStop(TripEntity trip, RouteMotion motion, double elapsed) {
        return checkInStates.findById(trip.getId()).map(state -> state.getNextStopSequence())
            .orElse(motion.at(elapsed).nextStopSequence());
    }
    private RouteMotion.Frame scenarioFrame(SimulationRunEntity run, RouteMotion.Frame frame) {
        if(run.getScenario()!=SimulationScenario.OFF_ROUTE) return frame;
        // Deterministic perpendicular offset of 350 m; never labels simulator samples as GPS.
        double bearing=Math.toRadians(frame.heading()+90);
        double latitude=frame.latitude()+Math.cos(bearing)*350/111_195d;
        double longitude=frame.longitude()+Math.sin(bearing)*350/(111_195d*Math.max(.01,Math.cos(Math.toRadians(frame.latitude()))));
        latitude=Math.max(-89.99,Math.min(89.99,latitude)); longitude=((longitude+540)%360)-180;
        return new RouteMotion.Frame(latitude,longitude,frame.heading(),frame.speedKmh(),frame.progressPercent(),frame.nextStopSequence(),
            frame.nextStopEtaSeconds(),frame.dwellRemainingSeconds(),frame.dwelling(),frame.finished());
    }
    private SimulationTrafficMetadata trafficMetadata(TripEntity trip, boolean snapshot) {
        try {
            TripEtaResponse eta = snapshot ? trafficEta.latestForSnapshot(trip) : trafficEta.calculate(trip.getId());
            if (eta == null) return new SimulationTrafficMetadata(TrafficSource.UNAVAILABLE, TrafficStatus.UNAVAILABLE,
                    null, null, null, false, "TRAFFIC_REQUIRES_ETA_QUERY");
            Long nextEta = eta.nextStopSequence() == null ? null : eta.stops().stream()
                    .filter(stop -> stop.sequenceNumber() == eta.nextStopSequence())
                    .map(stop -> stop.etaSeconds())
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
        try { return geometry.resolve(trip).motion(); }
        catch(IllegalArgumentException|ArithmeticException ex) { throw conflict("Geometry hoặc thời lượng tuyến không hợp lệ để mô phỏng. Hãy tính lại tuyến."); }
    }
    private Instant simulatedAt(TripEntity trip,SimulationRunEntity run) { return trip.simulationOriginAt().plusMillis((long)(run.getElapsedSeconds()*1000)); }
    private TripEntity lockTrip(long id) {
        var trip=trips.findLockedById(id).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Không tìm thấy chuyến."));
        vehicles.findLockedById(trip.getVehicle().getId()).orElseThrow();
        return trip;
    }
    private SimulationRunEntity requireRun(long tripId) { return runs.findByTripId(tripId).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Chưa có phiên mô phỏng.")); }
    private Instant now() { return operationsClock.instant().truncatedTo(ChronoUnit.MICROS); }
    private ResponseStatusException conflict(String text) { return new ResponseStatusException(CONFLICT,text); }
}
