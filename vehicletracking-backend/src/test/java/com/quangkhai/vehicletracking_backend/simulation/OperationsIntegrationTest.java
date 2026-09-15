package com.quangkhai.vehicletracking_backend.simulation;

import com.quangkhai.vehicletracking_backend.simulation.entity.SimulationStatus;
import com.quangkhai.vehicletracking_backend.simulation.service.*;
import com.quangkhai.vehicletracking_backend.simulation.repository.SimulationRepository;
import com.quangkhai.vehicletracking_backend.telemetry.dto.*;
import com.quangkhai.vehicletracking_backend.telemetry.entity.TelemetrySource;
import com.quangkhai.vehicletracking_backend.telemetry.repository.*;
import com.quangkhai.vehicletracking_backend.telemetry.service.*;
import com.quangkhai.vehicletracking_backend.checkin.service.CheckInQueryService;
import com.quangkhai.vehicletracking_backend.trip.TripFixtures;
import com.quangkhai.vehicletracking_backend.trip.dto.*;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import com.quangkhai.vehicletracking_backend.trip.service.TripService;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import com.quangkhai.vehicletracking_backend.station.entity.StationEntity;
import com.quangkhai.vehicletracking_backend.vehicle.repository.VehicleRepository;
import com.quangkhai.vehicletracking_backend.route.repository.RouteRepository;
import com.quangkhai.vehicletracking_backend.station.repository.StationRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest(properties={"here.routing.enabled=false","here.traffic.enabled=false","app.simulation.scheduling-enabled=false"})
class OperationsIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17");
    @Autowired SimulationService simulator;
    @Autowired TelemetryService telemetry;
    @Autowired TelemetryRepository samples;
    @Autowired VehiclePositionRepository positions;
    @Autowired SimulationRepository runs;
    @Autowired OperationsSnapshotService snapshots;
    @Autowired CheckInQueryService checkIns;
    @Autowired com.quangkhai.vehicletracking_backend.checkin.repository.TripStopVisitRepository visits;
    @Autowired com.quangkhai.vehicletracking_backend.trip.repository.TripRepository tripRepository;
    @Autowired TelemetryHistoryService history;
    @Autowired TripService trips;
    @Autowired StationRepository stations;
    @Autowired RouteRepository routes;
    @Autowired VehicleRepository vehicles;
    @MockitoBean Clock operationsClock;
    final AtomicReference<Instant> time=new AtomicReference<>();
    static final AtomicInteger ids=new AtomicInteger();
    @BeforeEach void clock() { time.set(Instant.now().truncatedTo(ChronoUnit.MICROS)); when(operationsClock.instant()).thenAnswer(call->time.get()); }
    private TripDetailResponse create() {
        var a=stations.saveAndFlush(TripFixtures.station("A")); var b=stations.saveAndFlush(TripFixtures.station("B"));
        var route=routes.saveAndFlush(SimulationFixtures.route(a,b));
        var vehicle=vehicles.saveAndFlush(new VehicleEntity("SIM"+ids.incrementAndGet(),"Xe thử 006",null));
        return trips.create(new TripCreateRequest(vehicle.getId(),route.getId(),time.get()));
    }
    private TripDetailResponse createAligned() {
        var a=stations.saveAndFlush(new StationEntity("A aligned",null,new BigDecimal("10.770000"),new BigDecimal("106.700000"),50));
        var b=stations.saveAndFlush(new StationEntity("B aligned",null,new BigDecimal("10.771000"),new BigDecimal("106.701000"),50));
        var route=routes.saveAndFlush(SimulationFixtures.route(a,b));
        var vehicle=vehicles.saveAndFlush(new VehicleEntity("CHK"+ids.incrementAndGet(),"Xe check-in",null));
        return trips.create(new TripCreateRequest(vehicle.getId(),route.getId(),time.get()));
    }
    private TelemetryRequest gps(TripDetailResponse trip,UUID event,Instant recorded,double latitude) {
        return new TelemetryRequest(event,trip.trip().vehicleId(),trip.trip().id(),recorded,latitude,106.7,30d,45d,5d,TelemetrySource.GPS);
    }
    private void seconds(long value) { time.updateAndGet(t->t.plusSeconds(value)); }
    private void conflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOfSatisfying(ResponseStatusException.class,ex->assertThat(ex.getStatusCode().value()).isEqualTo(409));
    }
    @Test void gpsDeduplicationHistoryAndOldPacketsNeverRegressLatest() {
        var trip=create(); trips.start(trip.trip().id()); var event=UUID.randomUUID();
        var request=gps(trip,event,time.get(),10.77);
        var first=telemetry.ingestGps(request); seconds(1);
        assertThat(telemetry.ingestGps(request).id()).isEqualTo(first.id());
        conflict(()->telemetry.ingestGps(gps(trip,event,request.recordedAt(),10.78)));
        conflict(()->telemetry.ingestGps(gps(trip,UUID.randomUUID(),request.recordedAt(),10.78)));
        var second=telemetry.ingestGps(gps(trip,UUID.randomUUID(),time.get(),10.78));
        conflict(()->telemetry.ingestGps(gps(trip,UUID.randomUUID(),time.get().minusSeconds(2),10.79)));
        assertThat(samples.countByTripId(trip.trip().id())).isEqualTo(2);
        assertThat(snapshots.snapshot().positions()).filteredOn(p->p.vehicleId()==trip.trip().vehicleId()).singleElement().isEqualTo(second);
        trips.complete(trip.trip().id());
        assertThat(telemetry.ingestGps(request).id()).isEqualTo(first.id()); // Retry still safe after trip ends.
        seconds(1); conflict(()->telemetry.ingestGps(gps(trip,UUID.randomUUID(),time.get(),10.78)));
    }
    @Test void invalidFutureAndSourceAreRejected() {
        var trip=create(); trips.start(trip.trip().id());
        assertThatThrownBy(()->telemetry.ingestGps(gps(trip,UUID.randomUUID(),time.get().plusSeconds(31),10.77)))
            .isInstanceOfSatisfying(ResponseStatusException.class,ex->assertThat(ex.getStatusCode().value()).isEqualTo(400));
        assertThatThrownBy(()->telemetry.ingestGps(gps(trip,UUID.randomUUID(),time.get(),Double.NaN))).isInstanceOf(ResponseStatusException.class);
        var fake=new TelemetryRequest(UUID.randomUUID(),trip.trip().vehicleId(),trip.trip().id(),time.get(),10.77,106.7,0d,0d,0d,TelemetrySource.SIMULATOR);
        assertThatThrownBy(()->telemetry.ingestGps(fake)).isInstanceOf(ResponseStatusException.class);
        assertThat(samples.countByTripId(trip.trip().id())).isZero();
    }
    @Test void wrongVehicleAndScheduledTripsRejectGps() {
        var trip=create();
        conflict(()->telemetry.ingestGps(gps(trip,UUID.randomUUID(),time.get(),10.77)));
        trips.start(trip.trip().id());
        var wrong=new TelemetryRequest(UUID.randomUUID(),trip.trip().vehicleId()+9999,trip.trip().id(),time.get(),10.77,106.7,0d,0d,0d,TelemetrySource.GPS);
        conflict(()->telemetry.ingestGps(wrong));
    }
    @Test void clockPauseResumeMultiplierAndCompletionKeepBaseline() {
        var trip=create();long id=trip.trip().id();
        var initial=simulator.play(id);
        assertThat(initial.status()).isEqualTo(SimulationStatus.RUNNING);
        seconds(5);simulator.tick(id);var paused=simulator.pause(id);
        assertThat(paused.elapsedSeconds()).isEqualTo(5);
        assertThat(paused.frame().speedKmh()).isZero();
        var count=samples.countByTripId(id);
        seconds(20);simulator.tick(id);
        assertThat(samples.countByTripId(id)).isEqualTo(count);
        simulator.speed(id,5);var resumed=simulator.play(id);
        assertThat(resumed.elapsedSeconds()).isEqualTo(5);
        assertThat(resumed.frame().speedKmh()).isCloseTo(initial.frame().speedKmh(),within(.0001));
        seconds(3);simulator.tick(id);
        var run=snapshots.snapshot().simulations().stream().filter(s->s.tripId()==id).findFirst().orElseThrow();
        assertThat(run.elapsedSeconds()).isEqualTo(20);assertThat(run.frame().dwelling()).isTrue();
        seconds(6);simulator.tick(id);
        assertThat(trips.findById(id).trip().status()).isEqualTo(TripStatus.COMPLETED);
        assertThat(trips.findById(id).stops()).isEqualTo(trip.stops());
        assertThat(runs.findByTripId(id).orElseThrow().getElapsedSeconds()).isEqualTo(44);
        var position=snapshots.snapshot().positions().stream().filter(p->p.tripId()==id).findFirst().orElseThrow();
        assertThat(position.speedKmh()).isZero();assertThat(position.source()).isEqualTo(TelemetrySource.SIMULATOR);
        assertThat(position.simulatedAt()).isEqualTo(trip.trip().scheduledDepartureAt().plusSeconds(44));
    }
    @Test void simulatorPersistsOrderedCheckinsFromRouteTrace() {
        var trip=createAligned(); long id=trip.trip().id();
        simulator.play(id);
        assertThat(checkIns.find(id).visits()).extracting(v->v.stopSequence()).containsExactly(1);
        assertThat(checkIns.find(id).revision()).isEqualTo(1);
        seconds(25); simulator.tick(id);
        assertThat(checkIns.find(id).visits()).extracting(v->v.stopSequence()).containsExactly(1,2);
        assertThat(checkIns.find(id).revision()).isEqualTo(2);
        assertThat(checkIns.find(id).visits().get(1).evidenceKind()).isEqualTo(com.quangkhai.vehicletracking_backend.checkin.entity.CheckInEvidenceKind.ROUTE_TRACE);
        seconds(19); simulator.tick(id);
        var result=checkIns.find(id);
        assertThat(result.visits()).extracting(v->v.stopSequence()).containsExactly(1,2,3);
        assertThat(result.revision()).isEqualTo(3);
        assertThat(result.visits()).extracting(v->v.source()).containsOnly(TelemetrySource.SIMULATOR);
        assertThat(result.nextStopSequence()).isNull();
        assertThat(result.awaitingExit()).isFalse();
        assertThat(trips.findById(id).trip().status()).isEqualTo(TripStatus.COMPLETED);
    }
    @Test void gpsPersistsPointThenInterpolatedSegmentCheckin() {
        var trip=createAligned(); long id=trip.trip().id(); trips.start(id);
        telemetry.ingestGps(new TelemetryRequest(UUID.randomUUID(),trip.trip().vehicleId(),id,time.get(),
            10.770000,106.700000,20d,90d,5d,TelemetrySource.GPS));
        seconds(5);
        telemetry.ingestGps(new TelemetryRequest(UUID.randomUUID(),trip.trip().vehicleId(),id,time.get(),
            10.771000,106.701000,20d,90d,5d,TelemetrySource.GPS));
        var result=checkIns.find(id);
        assertThat(result.visits()).extracting(v->v.stopSequence()).containsExactly(1,2);
        assertThat(result.revision()).isEqualTo(2);
        assertThat(result.visits().getFirst().evidenceKind()).isEqualTo(com.quangkhai.vehicletracking_backend.checkin.entity.CheckInEvidenceKind.POINT);
        assertThat(result.visits().get(1).evidenceKind()).isEqualTo(com.quangkhai.vehicletracking_backend.checkin.entity.CheckInEvidenceKind.SEGMENT);
        assertThat(result.visits().get(1).fromSampleId()).isNotNull();
    }
    @Test void resetRetainsHistoryAndIsIdempotent() {
        var trip=createAligned();long id=trip.trip().id();var first=simulator.play(id);seconds(3);simulator.tick(id);
        var count=samples.countByTripId(id);
        var oldVisits=checkIns.find(id).visits();
        var revision=checkIns.find(id).revision();
        long routeCount=routes.count(), tripCount=tripRepository.count();
        var replay=simulator.reset(id);
        assertThat(replay.tripId()).isEqualTo(id); assertThat(replay.id()).isEqualTo(first.id());
        assertThat(replay.attemptNumber()).isEqualTo(2); assertThat(replay.elapsedSeconds()).isZero();
        assertThat(replay.status()).isEqualTo(SimulationStatus.PAUSED);
        assertThat(simulator.reset(id).attemptNumber()).isEqualTo(2);
        assertThat(simulator.attempts(id)).hasSize(1);
        assertThat(trips.findById(id).trip().status()).isEqualTo(TripStatus.SCHEDULED);
        assertThat(trips.findById(id).trip().startedAt()).isNull();
        assertThat(trips.findById(id).trip().endedAt()).isNull();
        assertThat(trips.findById(id).trip().routeId()).isEqualTo(trip.trip().routeId());
        assertThat(routes.count()).isEqualTo(routeCount); assertThat(tripRepository.count()).isEqualTo(tripCount);
        assertThat(samples.countByTripId(id)).isEqualTo(count);
        assertThat(checkIns.find(id).visits()).isEmpty();
        assertThat(checkIns.find(id).revision()).isGreaterThan(revision);
        assertThat(checkIns.findAttempt(id,1).visits()).isEqualTo(oldVisits).isNotEmpty();
        assertThat(snapshots.snapshot().positions()).noneMatch(p -> p.tripId()==id);
        assertThat(history.find(id,null,null,null,null,0,100,1).totalElements()).isEqualTo(count);
        simulator.play(id);
        assertThat(checkIns.find(id).visits()).singleElement().satisfies(v -> {
            assertThat(v.stopSequence()).isEqualTo(1); assertThat(v.attemptNumber()).isEqualTo(2);
            assertThat(v.fromSampleId()).isNull();
        });
        assertThat(checkIns.findAttempt(id,1).visits()).isEqualTo(oldVisits);
        assertThat(history.find(id,null,null,null,null,0,100,2).totalElements()).isEqualTo(1);
        assertThat(snapshots.snapshot().positions()).filteredOn(p->p.tripId()==id).singleElement()
            .satisfies(p->assertThat(p.attemptNumber()).isEqualTo(2));
    }
    @Test void resetRejectsAnotherRunningTripWithoutArchiving() {
        var trip=create(); long id=trip.trip().id(); simulator.play(id); simulator.stop(id);
        var other=trips.create(new TripCreateRequest(trip.trip().vehicleId(),trip.trip().routeId(),time.get()));
        trips.start(other.trip().id());
        conflict(()->simulator.reset(id));
        assertThat(simulator.attempts(id)).isEmpty();
        assertThat(trips.findById(id).trip().attemptNumber()).isEqualTo(1);
    }
    @Test void restartPausesWithoutAdvancingAcrossDowntime() {
        var trip=create();long id=trip.trip().id();simulator.play(id);seconds(2);simulator.tick(id);
        seconds(500);simulator.recover(id);
        var run=runs.findByTripId(id).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(SimulationStatus.PAUSED);assertThat(run.getElapsedSeconds()).isEqualTo(2);
        seconds(2);simulator.tick(id);assertThat(runs.findByTripId(id).orElseThrow().getElapsedSeconds()).isEqualTo(2);
    }
    @Test void gpsAndSimulatorCannotShareTrip() {
        var gpsTrip=create();trips.start(gpsTrip.trip().id());telemetry.ingestGps(gps(gpsTrip,UUID.randomUUID(),time.get(),10.77));
        conflict(()->simulator.play(gpsTrip.trip().id()));
        var simulated=create();simulator.play(simulated.trip().id());seconds(1);
        conflict(()->telemetry.ingestGps(gps(simulated,UUID.randomUUID(),time.get(),10.77)));
    }
    @Test void manualCancelStopsFutureTicks() {
        var trip=create();long id=trip.trip().id();simulator.play(id);
        var count=samples.countByTripId(id);trips.cancel(id);seconds(2);simulator.tick(id);
        assertThat(runs.findByTripId(id).orElseThrow().getStatus()).isEqualTo(SimulationStatus.STOPPED);
        assertThat(samples.countByTripId(id)).isEqualTo(count);
    }
    @Test void concurrentPlayCreatesOneRunAndSameVehicleCannotRunTwoTrips() throws Exception {
        var trip=create();long id=trip.trip().id();
        try(var executor=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            var first=executor.submit(()->{ gate.await(); return simulator.play(id).id(); });
            var second=executor.submit(()->{ gate.await(); return simulator.play(id).id(); });
            gate.countDown();
            assertThat(first.get(15,TimeUnit.SECONDS)).isEqualTo(second.get(15,TimeUnit.SECONDS));
        }
        var other=trips.create(new TripCreateRequest(trip.trip().vehicleId(),trip.trip().routeId(),time.get()));
        conflict(()->simulator.play(other.trip().id()));
        assertThat(runs.findByTripId(other.trip().id())).isEmpty();
    }
    @Test void corruptRouteCannotStartOrCreateRun() {
        var a=stations.saveAndFlush(TripFixtures.station("Bad A"));var b=stations.saveAndFlush(TripFixtures.station("Bad B"));
        var route=routes.saveAndFlush(TripFixtures.route(a,b));var vehicle=vehicles.saveAndFlush(new VehicleEntity("BAD"+ids.incrementAndGet(),"Bad",null));
        var trip=trips.create(new TripCreateRequest(vehicle.getId(),route.getId(),time.get()));
        conflict(()->simulator.play(trip.trip().id()));
        assertThat(trips.findById(trip.trip().id()).trip().status()).isEqualTo(TripStatus.SCHEDULED);
        assertThat(runs.findByTripId(trip.trip().id())).isEmpty();
    }
}
