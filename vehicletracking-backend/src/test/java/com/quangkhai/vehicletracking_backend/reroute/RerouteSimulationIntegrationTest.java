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
}
