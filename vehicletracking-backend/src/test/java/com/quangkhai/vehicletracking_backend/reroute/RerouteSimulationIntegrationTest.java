package com.quangkhai.vehicletracking_backend.reroute;

import com.quangkhai.vehicletracking_backend.checkin.service.CheckInQueryService;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripTrafficAlertStateRepository;
import com.quangkhai.vehicletracking_backend.reroute.service.RerouteEvaluationService;
import com.quangkhai.vehicletracking_backend.route.repository.RouteRepository;
import com.quangkhai.vehicletracking_backend.simulation.SimulationFixtures;
import com.quangkhai.vehicletracking_backend.simulation.entity.SimulationStatus;
import com.quangkhai.vehicletracking_backend.simulation.repository.SimulationRepository;
import com.quangkhai.vehicletracking_backend.simulation.service.SimulationService;
import com.quangkhai.vehicletracking_backend.station.entity.StationEntity;
import com.quangkhai.vehicletracking_backend.station.repository.StationRepository;
import com.quangkhai.vehicletracking_backend.traffic.*;
import com.quangkhai.vehicletracking_backend.traffic.controller.TrafficEtaController;
import com.quangkhai.vehicletracking_backend.traffic.eta.TripEtaResponse;
import com.quangkhai.vehicletracking_backend.traffic.service.TrafficQueryService;
import com.quangkhai.vehicletracking_backend.trip.dto.TripCreateRequest;
import com.quangkhai.vehicletracking_backend.trip.dto.TripDetailResponse;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import com.quangkhai.vehicletracking_backend.trip.service.TripService;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import com.quangkhai.vehicletracking_backend.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Real JPA/transactions with deterministic provider responses, including the HERE_LIVE branch. */
@Testcontainers
@SpringBootTest(properties = {"here.routing.enabled=false", "here.traffic.enabled=false",
        "app.simulation.scheduling-enabled=false"})
class RerouteSimulationIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");
    @Autowired SimulationService simulator;
    @Autowired SimulationRepository runs;
    @Autowired TripService trips;
    @Autowired StationRepository stations;
    @Autowired RouteRepository routes;
    @Autowired VehicleRepository vehicles;
    @Autowired TripTrafficAlertStateRepository states;
    @Autowired RerouteEvaluationService reroutes;
    @Autowired TrafficEtaController etaController;
    @Autowired CheckInQueryService checkIns;
    @Autowired com.quangkhai.vehicletracking_backend.reroute.repository.TripRouteRevisionRepository revisions;
    @Autowired com.quangkhai.vehicletracking_backend.trip.repository.TripRepository tripRepository;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired com.quangkhai.vehicletracking_backend.reroute.service.TripRouteGeometryQueryService routeQuery;
    @MockitoBean Clock operationsClock;
    @MockitoBean TrafficQueryService traffic;
    final AtomicReference<Instant> time = new AtomicReference<>();

    @BeforeEach void setup() {
        time.set(Instant.now().truncatedTo(ChronoUnit.MICROS));
        when(operationsClock.instant()).thenAnswer(call -> time.get());
        when(traffic.flowForEta(any())).thenAnswer(call -> new TrafficEnvelope<TrafficFlowSegment>(
                TrafficSource.HERE_LIVE, TrafficStatus.AVAILABLE, time.get(), time.get(), 60, null, List.of()));
        when(traffic.incidentsForEta(any())).thenAnswer(call -> new TrafficEnvelope<TrafficIncident>(
                TrafficSource.HERE_LIVE, TrafficStatus.AVAILABLE, time.get(), time.get(), 60, null, List.of()));
    }

    private TripDetailResponse create() {
        var a = stations.saveAndFlush(new StationEntity("A", null,
                new BigDecimal("10.770000"), new BigDecimal("106.700000"), 50));
        var b = stations.saveAndFlush(new StationEntity("B", null,
                new BigDecimal("10.771000"), new BigDecimal("106.701000"), 50));
        var route = routes.saveAndFlush(SimulationFixtures.route(a, b));
        var vehicle = vehicles.saveAndFlush(new VehicleEntity("RT" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT), "Test", null));
        return trips.create(new TripCreateRequest(vehicle.getId(), route.getId(), time.get()));
    }

    @Test void firstLiveEvaluationPersistsSharedPrimaryKeyAndUpdatesExistingCheckpoint() {
        var trip = create();
        long id = trip.trip().id();
        trips.start(id);
        for (int i = 0; i < 3; i++) {
            time.updateAndGet(t -> t.plusSeconds(1));
            var eta = new TripEtaResponse(id, trip.trip().routeId(), time.get(), TrafficSource.HERE_LIVE,
                    TrafficStatus.AVAILABLE, time.get(), time.get(), 2, 44, 44, List.of(), List.of(), null);
            reroutes.evaluate(id, eta);
            var state = states.findById(id).orElseThrow();
            assertThat(state.getTripId()).isEqualTo(id);
            assertThat(state.getLastTrafficFetchedAt()).isEqualTo(time.get());
            assertThat(state.getBreachCount()).isZero();
        }
    }

    @Test void liveTrafficSimulationPersistsCheckpointAndCompletesWithEtaReadsAndCheckins() {
        long id = create().trip().id();
        simulator.play(id);
        // afterCommit errors are best-effort: checking RUNNING alone would miss the original bug.
        assertThat(states.findById(id)).isPresent();
        assertThat(etaController.calculate(id).source()).isEqualTo(TrafficSource.HERE_LIVE);
        time.updateAndGet(t -> t.plusSeconds(5));
        simulator.tick(id);
        assertThat(simulator.pause(id).status()).isEqualTo(SimulationStatus.PAUSED);
        simulator.speed(id, 10);
        simulator.play(id);
        for (int i = 0; i < 10 && trips.findById(id).trip().status() == TripStatus.IN_PROGRESS; i++) {
            time.updateAndGet(t -> t.plusSeconds(1));
            simulator.tick(id);
            assertThatCode(() -> etaController.calculate(id)).doesNotThrowAnyException();
        }
        var run = runs.findByTripId(id).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(SimulationStatus.COMPLETED);
        assertThat(run.getErrorMessage()).isNull();
        assertThat(trips.findById(id).trip().status()).isEqualTo(TripStatus.COMPLETED);
        assertThat(checkIns.find(id).visits()).extracting(v -> v.stopSequence()).containsExactly(1, 2, 3);
    }

    @Test void cachedTrafficDoesNotEraseConsecutiveBreachBeforeNextRefresh() {
        var trip=create();long id=trip.trip().id();trips.start(id);
        var first=new TripEtaResponse(id,trip.trip().routeId(),time.get(),TrafficSource.HERE_LIVE,TrafficStatus.AVAILABLE,
            time.get(),time.get(),2,100,1000,List.of(),List.of(),null);
        reroutes.evaluate(id,first);
        for(int i=0;i<4;i++) reroutes.evaluate(id,first);
        assertThat(states.findById(id).orElseThrow().getBreachCount()).isEqualTo(1);
        time.updateAndGet(t -> t.plusSeconds(61));
        reroutes.evaluate(id,new TripEtaResponse(id,trip.trip().routeId(),time.get(),TrafficSource.HERE_LIVE,TrafficStatus.AVAILABLE,
            time.get(),time.get(),2,100,1000,List.of(),List.of(),null));
        assertThat(states.findById(id).orElseThrow().getLastTriggerAt()).isNotNull();
    }

    @Test void simulatorAdoptsPersistedDetourAndReplayReturnsToOriginalRoute() {
        var detail=create();long id=detail.trip().id();simulator.play(id);
        time.updateAndGet(t -> t.plusSeconds(5));simulator.tick(id);
        var before=simulator.pause(id);simulator.play(id);
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        long revisionId=tx.execute(status -> {
            var trip=tripRepository.findById(id).orElseThrow();
            var revision=new com.quangkhai.vehicletracking_backend.reroute.entity.TripRouteRevisionEntity(trip,trip.getRoute(),1,
                com.quangkhai.vehicletracking_backend.reroute.entity.RerouteReasonCode.TRAFFIC_DELAY,"detour",null,
                com.quangkhai.vehicletracking_backend.reroute.entity.NotificationSeverity.MAJOR,100,44,time.get());
            revision.addSection(new com.quangkhai.vehicletracking_backend.reroute.entity.TripRouteRevisionSectionEntity(1,2,
                SimulationFixtures.encode(new double[][]{{before.frame().latitude(),before.frame().longitude()},{10.77025,106.702},{10.771,106.701}}),400,20,20));
            revision.addSection(new com.quangkhai.vehicletracking_backend.reroute.entity.TripRouteRevisionSectionEntity(2,3,
                SimulationFixtures.encode(new double[][]{{10.771,106.701},{10.77,106.70}}),156,20,20));
            for(var stop:trip.getStops()) if(stop.getSequenceNumber()>1) revision.addStop(new com.quangkhai.vehicletracking_backend.reroute.entity.TripRouteRevisionStopEntity(
                stop.getSequenceNumber(),stop.getSequenceNumber()-1,stop.getStationId(),stop.getStationName(),stop.getLatitude(),stop.getLongitude(),stop.getDwellDurationSeconds(),
                stop.getPlannedArrivalAt(),stop.getPlannedDepartureAt(),time.get().plusSeconds(20),time.get().plusSeconds(24)));
            return revisions.saveAndFlush(revision).getId();
        });
        time.updateAndGet(t -> t.plusSeconds(10));simulator.tick(id);
        var detour=simulator.pause(id);
        assertThat(detour.routeRevisionId()).isEqualTo(revisionId);
        assertThat(detour.frame().longitude()).isGreaterThan(106.701);
        assertThat(routeQuery.get(id).sections()).hasSize(3);
        tx.executeWithoutResult(status -> {
            var restarted=new com.quangkhai.vehicletracking_backend.reroute.service.TripRouteGeometryService(revisions);
            var recovered=restarted.resolve(tripRepository.findById(id).orElseThrow());
            assertThat(recovered.motion().at(detour.elapsedSeconds()).longitude()).isEqualTo(detour.frame().longitude());
        });
        simulator.play(id);simulator.speed(id,10);
        for(int i=0;i<10 && trips.findById(id).trip().status()==TripStatus.IN_PROGRESS;i++) { time.updateAndGet(t -> t.plusSeconds(1));simulator.tick(id); }
        assertThat(checkIns.find(id).visits()).extracting(v -> v.stopSequence()).containsExactly(1,2,3);
        var replay=simulator.reset(id);
        assertThat(replay.routeRevisionId()).isNull();assertThat(replay.attemptNumber()).isEqualTo(2);
        assertThat(routeQuery.get(id).sections()).hasSize(2);
    }
}
