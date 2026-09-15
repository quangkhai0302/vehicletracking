package com.quangkhai.vehicletracking_backend.trip.repository;

import com.quangkhai.vehicletracking_backend.trip.TripFixtures;
import com.quangkhai.vehicletracking_backend.trip.dto.*;
import com.quangkhai.vehicletracking_backend.trip.entity.*;
import com.quangkhai.vehicletracking_backend.trip.service.TripService;
import com.quangkhai.vehicletracking_backend.vehicle.dto.VehicleUpsertRequest;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleType;
import com.quangkhai.vehicletracking_backend.vehicle.repository.VehicleRepository;
import com.quangkhai.vehicletracking_backend.vehicle.service.VehicleService;
import com.quangkhai.vehicletracking_backend.route.entity.RouteEntity;
import com.quangkhai.vehicletracking_backend.route.repository.RouteRepository;
import com.quangkhai.vehicletracking_backend.station.dto.StationUpsertRequest;
import com.quangkhai.vehicletracking_backend.station.repository.StationRepository;
import com.quangkhai.vehicletracking_backend.station.service.StationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(properties = {"here.routing.enabled=false", "here.traffic.enabled=false"})
class FleetRepositoryIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");
    @Autowired VehicleRepository vehicles;
    @Autowired RouteRepository routes;
    @Autowired StationRepository stations;
    @Autowired TripRepository trips;
    @Autowired TripService service;
    @Autowired VehicleService vehicleService;
    @Autowired StationService stationService;
    static final AtomicInteger sequence = new AtomicInteger();
    final Instant departure = Instant.parse("2026-09-13T16:58:00Z");

    private record Fixture(VehicleEntity vehicle, RouteEntity route, long stationId) {}
    private Fixture fixture() {
        var a = stations.saveAndFlush(TripFixtures.station("A"));
        var b = stations.saveAndFlush(TripFixtures.station("B"));
        var route = routes.saveAndFlush(TripFixtures.route(a, b));
        var vehicle = vehicles.saveAndFlush(new VehicleEntity("TEST" + sequence.incrementAndGet(), "Xe thử", null));
        return new Fixture(vehicle, route, a.getId());
    }
    private TripDetailResponse create(Fixture f) {
        return service.create(new TripCreateRequest(f.vehicle().getId(), f.route().getId(), departure));
    }
    @Test void persistsMotorcycleTypeAndExposesItThroughTripSummary() {
        var response = vehicleService.create(new VehicleUpsertRequest("59X1" + sequence.incrementAndGet(),
                "Xe máy thử", null, VehicleType.MOTORCYCLE));
        assertThat(response.vehicleType()).isEqualTo(VehicleType.MOTORCYCLE);
        assertThat(vehicles.findById(response.id()).orElseThrow().getVehicleType()).isEqualTo(VehicleType.MOTORCYCLE);

        var a = stations.saveAndFlush(TripFixtures.station("Moto A"));
        var b = stations.saveAndFlush(TripFixtures.station("Moto B"));
        var route = routes.saveAndFlush(TripFixtures.route(a, b));
        var trip = service.create(new TripCreateRequest(response.id(), route.getId(), departure));
        assertThat(trip.trip().vehicleType()).isEqualTo(VehicleType.MOTORCYCLE);
    }
    @Test void persistsImmutableStopScheduleRadiusAndPlateAfterStationAndVehicleUpdates() {
        var f = fixture(); var created = create(f); var id = created.trip().id();
        stationService.update(f.stationId(), new StationUpsertRequest("New station", "New address",
                new BigDecimal("11.000000"), new BigDecimal("107.000000"), 500));
        vehicleService.update(f.vehicle().getId(), new VehicleUpsertRequest("NEW" + sequence.incrementAndGet(), "Updated", null));
        var loaded = service.findById(id);
        assertThat(loaded.stops()).hasSize(3);
        assertThat(loaded.stops().getFirst().stationName()).isEqualTo("A");
        assertThat(loaded.stops().getFirst().checkinRadiusMeters()).isEqualTo(50);
        assertThat(loaded.stops().getFirst().latitude()).isEqualByComparingTo("10.772300");
        assertThat(loaded.stops().get(1).plannedDepartureAt()).isEqualTo(departure.plusSeconds(360));
        assertThat(loaded.trip().vehiclePlateNumber()).isEqualTo(f.vehicle().getPlateNumber());
        service.start(id);
        var completed = service.complete(id);
        assertThat(service.complete(id).trip().endedAt()).isEqualTo(completed.trip().endedAt());
        assertThat(completed.trip().scheduledDepartureAt()).isEqualTo(departure);
        vehicleService.deactivate(f.vehicle().getId());
        assertThat(service.findById(id).trip().status()).isEqualTo(TripStatus.COMPLETED);
    }
    @Test void uniquePlateAndDatabaseStateChecksAreEnforced() {
        var f = fixture();
        assertThatThrownBy(() -> vehicles.saveAndFlush(new VehicleEntity(f.vehicle().getPlateNumber(), "Duplicate", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
        var inconsistent = new TripEntity(f.vehicle(), f.route(), departure);
        inconsistent.start(Instant.now()); inconsistent.complete(Instant.now().minusSeconds(60));
        assertThatThrownBy(() -> trips.saveAndFlush(inconsistent)).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void partialUniqueIndexBlocksTwoRunningTripsEvenOutsideService() {
        var f = fixture();
        var one = new TripEntity(f.vehicle(), f.route(), departure); one.start(Instant.now());
        trips.saveAndFlush(one);
        var two = new TripEntity(f.vehicle(), f.route(), departure); two.start(Instant.now());
        assertThatThrownBy(() -> trips.saveAndFlush(two)).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void concurrentStartsAllowExactlyOneWinner() throws Exception {
        var f = fixture(); var a = create(f).trip().id(); var b = create(f).trip().id();
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> results = List.of(a,b).stream().map(id -> executor.submit(() -> {
                gate.await();
                try { service.start(id); return 200; }
                catch(ResponseStatusException ex) { return ex.getStatusCode().value(); }
            })).toList();
            gate.countDown();
            assertThat(List.of(results.get(0).get(20, TimeUnit.SECONDS), results.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200,409);
        }
        assertThat(service.findAll(f.vehicle().getId())).filteredOn(t -> t.status() == TripStatus.IN_PROGRESS).hasSize(1);
    }
    @Test void deactivationRequiresScheduledTripToBeCancelled() {
        var f = fixture(); var trip = create(f);
        assertThatThrownBy(() -> vehicleService.deactivate(f.vehicle().getId())).isInstanceOf(ResponseStatusException.class);
        service.cancel(trip.trip().id()); vehicleService.deactivate(f.vehicle().getId());
        assertThat(vehicleService.findById(f.vehicle().getId()).active()).isFalse();
        assertThatThrownBy(() -> create(f)).isInstanceOf(ResponseStatusException.class);
    }
}
