package com.quangkhai.vehicletracking_backend.assignment;

import com.quangkhai.vehicletracking_backend.assignment.entity.TripAssignmentRequestEntity;
import com.quangkhai.vehicletracking_backend.assignment.repository.TripAssignmentRequestRepository;
import com.quangkhai.vehicletracking_backend.auth.entity.UserAccountEntity;
import com.quangkhai.vehicletracking_backend.auth.entity.UserRole;
import com.quangkhai.vehicletracking_backend.auth.repository.UserAccountRepository;
import com.quangkhai.vehicletracking_backend.driver.entity.DriverEntity;
import com.quangkhai.vehicletracking_backend.driver.repository.DriverRepository;
import com.quangkhai.vehicletracking_backend.route.repository.RouteRepository;
import com.quangkhai.vehicletracking_backend.station.entity.StationEntity;
import com.quangkhai.vehicletracking_backend.station.repository.StationRepository;
import com.quangkhai.vehicletracking_backend.trip.TripFixtures;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import com.quangkhai.vehicletracking_backend.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = {
        "here.routing.enabled=false", "here.traffic.enabled=false",
        "trip-scheduling.enabled=false", "trip-dispatch.enabled=false",
        "app.simulation.scheduling-enabled=false", "auth.security-enabled=false"
})
class TripAssignmentMigrationIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    private static final AtomicInteger IDS = new AtomicInteger();
    private static final Instant DEPARTURE = Instant.parse("2026-10-15T08:00:00Z");

    @Autowired JdbcTemplate jdbc;
    @Autowired VehicleRepository vehicles;
    @Autowired DriverRepository drivers;
    @Autowired UserAccountRepository accounts;
    @Autowired RouteRepository routes;
    @Autowired StationRepository stations;
    @Autowired TripRepository trips;
    @Autowired TripAssignmentRequestRepository requests;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void v22CreatesRequestConstraintsAndPartialUniqueIndexes() {
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = 'vehicle_tracking' and table_name = 'trip_assignment_requests'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from pg_indexes
                where schemaname = 'vehicle_tracking'
                  and indexname in ('uq_trip_assignment_pending_trip', 'uq_trip_assignment_pending_driver')
                """, Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.check_constraints
                where constraint_schema = 'vehicle_tracking'
                  and constraint_name in ('chk_trip_assignment_status', 'chk_trip_assignment_response')
                """, Integer.class)).isEqualTo(2);
    }

    @Test
    void pendingRequestIsUniquePerTripAndDriver() {
        Fixture fixture = fixture();
        requests.saveAndFlush(new TripAssignmentRequestEntity(fixture.trip(), fixture.driver(), fixture.admin(), DEPARTURE));

        assertThatThrownBy(() -> requests.saveAndFlush(
                new TripAssignmentRequestEntity(fixture.trip(), fixture.driver(), fixture.admin(), DEPARTURE.plusSeconds(1))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void concurrentPendingInsertsHaveOneWinner() throws Exception {
        Fixture fixture = fixture();
        var barrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(() -> insertConcurrently(fixture, barrier));
            Future<Boolean> second = pool.submit(() -> insertConcurrently(fixture, barrier));
            List<Boolean> results = List.of(first.get(), second.get());
            assertThat(results).containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean insertConcurrently(Fixture fixture, CyclicBarrier barrier) {
        try {
            return new TransactionTemplate(transactionManager).execute(status -> {
                var trip = trips.findById(fixture.trip().getId()).orElseThrow();
                var driver = drivers.findById(fixture.driver().getId()).orElseThrow();
                var admin = accounts.findById(fixture.admin().getId()).orElseThrow();
                await(barrier);
                requests.saveAndFlush(new TripAssignmentRequestEntity(trip, driver, admin, DEPARTURE));
                return true;
            });
        } catch (DataIntegrityViolationException | org.springframework.transaction.UnexpectedRollbackException ex) {
            return false;
        }
    }

    private Fixture fixture() {
        int n = IDS.incrementAndGet();
        var stationA = stations.saveAndFlush(new StationEntity("A-" + n, null,
                new java.math.BigDecimal("10.770000"), new java.math.BigDecimal("106.700000"), 50));
        var stationB = stations.saveAndFlush(new StationEntity("B-" + n, null,
                new java.math.BigDecimal("10.771000"), new java.math.BigDecimal("106.701000"), 50));
        var route = routes.saveAndFlush(TripFixtures.route(stationA, stationB));
        var vehicle = vehicles.saveAndFlush(new VehicleEntity("V22" + n, "Xe V22 " + n, null));
        var driver = drivers.saveAndFlush(new DriverEntity("Tài xế V22 " + n,
                "09" + String.format("%08d", n), "V22-L" + n));
        var admin = accounts.saveAndFlush(new UserAccountEntity("v22-admin-" + n, "hash", UserRole.ADMIN, null));
        accounts.saveAndFlush(new UserAccountEntity("v22-driver-" + n, "hash", UserRole.DRIVER, driver));
        var trip = trips.saveAndFlush(new TripEntity(vehicle, route, DEPARTURE));
        return new Fixture(trip, driver, admin);
    }

    private void await(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (Exception ex) {
            throw new IllegalStateException("Concurrency barrier failed", ex);
        }
    }

    private record Fixture(TripEntity trip, DriverEntity driver, UserAccountEntity admin) {}
}
