package com.quangkhai.vehicletracking_backend.reporting;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class EmployeeOccupancyMigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Test
    void upgradesV36WithoutInventingSeatOrBoardingValuesAndEnforcesNewSchema() {
        flyway("36").migrate();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        Long vehicleId = jdbc.queryForObject("""
                INSERT INTO vehicle_tracking.vehicles(plate_number, name, created_at, updated_at)
                VALUES ('OCCUPANCY36', 'Legacy vehicle', now(), now()) RETURNING id
                """, Long.class);

        assertThat(flyway("37").migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT seat_capacity FROM vehicle_tracking.vehicles WHERE id = ?",
                Integer.class, vehicleId)).isNull();
        jdbc.update("UPDATE vehicle_tracking.vehicles SET seat_capacity = 32 WHERE id = ?", vehicleId);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE vehicle_tracking.vehicles SET seat_capacity = 0 WHERE id = ?", vehicleId))
                .isInstanceOf(DataAccessException.class);

        assertThat(columnExists(jdbc, "trip_stops", "expected_employee_boarding_count")).isTrue();
        assertThat(columnExists(jdbc, "trip_stop_visits", "employee_boarding_count")).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'vehicle_tracking' AND table_name = 'trip_schedule_stop_boardings'
                """, Integer.class)).isEqualTo(1);
    }

    private boolean columnExists(JdbcTemplate jdbc, String table, String column) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = 'vehicle_tracking' AND table_name = ? AND column_name = ?
                """, Integer.class, table, column) == 1;
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .defaultSchema("vehicle_tracking").schemas("vehicle_tracking").target(target).load();
    }
}
