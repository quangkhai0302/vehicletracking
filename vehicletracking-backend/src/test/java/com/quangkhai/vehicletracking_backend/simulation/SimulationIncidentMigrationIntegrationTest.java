package com.quangkhai.vehicletracking_backend.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class SimulationIncidentMigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Test
    void upgradesV37AndV39AndEnforcesIncidentIdentityAndLifecycleConstraints() {
        flyway("37").migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        Long vehicle = jdbc.queryForObject("""
                INSERT INTO vehicle_tracking.vehicles(plate_number, name, created_at, updated_at)
                VALUES ('INCIDENT38', 'Incident test', now(), now()) RETURNING id
                """, Long.class);
        Long route = jdbc.queryForObject("""
                INSERT INTO vehicle_tracking.routes(name, transport_mode, routing_provider, total_distance_meters,
                estimated_travel_duration_seconds, base_travel_duration_seconds, total_dwell_duration_seconds,
                estimated_trip_duration_seconds, estimated_departure_at, calculated_at)
                VALUES ('Incident route', 'CAR', 'HERE', 100, 20, 20, 0, 20, now(), now()) RETURNING id
                """, Long.class);
        Long trip = jdbc.queryForObject("""
                INSERT INTO vehicle_tracking.trips(vehicle_id, route_id, vehicle_plate_snapshot, scheduled_departure_at, created_at)
                VALUES (?, ?, 'INCIDENT38', now(), now()) RETURNING id
                """, Long.class, vehicle, route);
        assertThat(flyway("38").migrate().migrationsExecuted).isEqualTo(1);

        Long incident = jdbc.queryForObject("""
                INSERT INTO vehicle_tracking.simulation_incidents(trip_id, attempt_number, type, severity, status,
                latitude, longitude, simulated_elapsed_seconds, idempotency_key, created_at)
                VALUES (?, 1, 'VEHICLE_BREAKDOWN', 'MAJOR', 'OPEN', 10.77, 106.7, 12,
                '7d8f1f35-a3a2-4229-9a24-a4c76c1d4b40', now()) RETURNING id
                """, Long.class, trip);
        jdbc.update("""
                INSERT INTO vehicle_tracking.trip_notifications(trip_id, type, severity, title, reason,
                affected_stop_sequences, dedupe_key, created_at, simulation_incident_id)
                VALUES (?, 'SIMULATION_INCIDENT', 'MAJOR', 'Xe gặp sự cố', 'Đã dừng kiểm tra', '',
                'simulation-incident-test', now(), ?)
                """, trip, incident);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO vehicle_tracking.simulation_incidents(trip_id, attempt_number, type, severity, status,
                latitude, longitude, simulated_elapsed_seconds, idempotency_key, created_at)
                VALUES (?, 1, 'OTHER', 'MAJOR', 'OPEN', 10.77, 106.7, 15,
                '7d8f1f35-a3a2-4229-9a24-a4c76c1d4b41', now())
                """, trip)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE vehicle_tracking.simulation_incidents SET status='RESOLVED' WHERE id=?", incident))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE vehicle_tracking.simulation_incidents SET latitude=91 WHERE id=?", incident))
                .isInstanceOf(DataAccessException.class);
        Long driver = jdbc.queryForObject("""
                INSERT INTO vehicle_tracking.drivers(full_name, phone_number, license_number, created_at, updated_at)
                VALUES ('Incident driver', '0900000001', 'INCIDENT-DRIVER', now(), now()) RETURNING id
                """, Long.class);
        assertThat(flyway("39").migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT reported_by_driver_id FROM vehicle_tracking.simulation_incidents WHERE id=?", Long.class, incident)).isNull();
        jdbc.update("UPDATE vehicle_tracking.simulation_incidents SET reported_by_driver_id=? WHERE id=?", driver, incident);
        assertThat(jdbc.queryForObject("SELECT reported_by_driver_id FROM vehicle_tracking.simulation_incidents WHERE id=?", Long.class, incident)).isEqualTo(driver);
        assertThatThrownBy(() -> jdbc.update("UPDATE vehicle_tracking.simulation_incidents SET reported_by_driver_id=999999 WHERE id=?", incident))
                .isInstanceOf(DataAccessException.class);
        jdbc.update("UPDATE vehicle_tracking.simulation_incidents SET status='ACKNOWLEDGED', acknowledged_at=now() WHERE id=?", incident);
        jdbc.update("UPDATE vehicle_tracking.simulation_incidents SET status='RESOLVED', resolved_at=now() WHERE id=?", incident);
        assertThat(jdbc.queryForObject("SELECT status FROM vehicle_tracking.simulation_incidents WHERE id=?", String.class, incident))
                .isEqualTo("RESOLVED");
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .defaultSchema("vehicle_tracking").schemas("vehicle_tracking").target(target).load();
    }
}
