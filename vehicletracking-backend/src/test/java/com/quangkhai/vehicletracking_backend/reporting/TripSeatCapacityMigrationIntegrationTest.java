package com.quangkhai.vehicletracking_backend.reporting;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
class TripSeatCapacityMigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Test
    void preservesUnknownHistoricalCapacityAndEnforcesPositiveFrozenSeats() {
        flyway("41").migrate();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        Long vehicleId = jdbc.queryForObject("""
                INSERT INTO vehicle_tracking.vehicles(plate_number, name, created_at, updated_at, seat_capacity)
                VALUES ('SEATS42', 'Vehicle fixture', now(), now(), 10) RETURNING id
                """, Long.class);
        Long routeId = jdbc.queryForObject("""
                INSERT INTO vehicle_tracking.routes(name, transport_mode, routing_provider,
                total_distance_meters, estimated_travel_duration_seconds, base_travel_duration_seconds,
                total_dwell_duration_seconds, estimated_trip_duration_seconds, estimated_departure_at, calculated_at)
                VALUES ('Route fixture', 'CAR', 'HERE', 1000, 600, 600, 0, 600, now(), now()) RETURNING id
                """, Long.class);
        Long tripId = jdbc.queryForObject("""
                INSERT INTO vehicle_tracking.trips(vehicle_id, route_id, vehicle_plate_snapshot,
                scheduled_departure_at, status, started_at, ended_at, created_at)
                VALUES (?, ?, 'SEATS42', now(), 'COMPLETED', now(), now(), now()) RETURNING id
                """, Long.class, vehicleId, routeId);
        assertThat(flyway("42").migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT seat_capacity_snapshot FROM vehicle_tracking.trips WHERE id = ?",
                Integer.class, tripId)).isNull();
        jdbc.update("UPDATE vehicle_tracking.trips SET seat_capacity_snapshot = 10 WHERE id = ?", tripId);
        jdbc.update("UPDATE vehicle_tracking.vehicles SET seat_capacity = 20 WHERE id = ?", vehicleId);
        assertThat(jdbc.queryForObject("SELECT seat_capacity_snapshot FROM vehicle_tracking.trips WHERE id = ?",
                Integer.class, tripId)).isEqualTo(10);
        for (int invalid : new int[]{0, -1}) assertThatThrownBy(() -> jdbc.update(
                "UPDATE vehicle_tracking.trips SET seat_capacity_snapshot = ? WHERE id = ?", invalid, tripId))
                .isInstanceOf(DataAccessException.class);
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .defaultSchema("vehicle_tracking").schemas("vehicle_tracking").target(target).load();
    }
}
