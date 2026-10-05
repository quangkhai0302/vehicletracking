package com.quangkhai.vehicletracking_backend.driver.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class DriverAvatarRemovalMigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Test
    void v24RemovesStoredAvatarsAndPreservesDriverProfiles() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .defaultSchema("vehicle_tracking")
                .schemas("vehicle_tracking")
                .target("23")
                .load().migrate();

        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        Long driverId = jdbc.queryForObject("""
                insert into vehicle_tracking.drivers
                    (full_name, phone_number, license_number, active, created_at, updated_at,
                     avatar_data, avatar_content_type)
                values ('Tài xế migration', '0901234567', 'V24-FIXTURE', true,
                        '2026-10-01T01:00:00Z', '2026-10-01T02:00:00Z', ?, 'image/png')
                returning id
                """, Long.class, new byte[]{1, 2, 3});
        var profileBefore = jdbc.queryForMap("""
                select id, full_name, phone_number, license_number, active, created_at, updated_at
                from vehicle_tracking.drivers where id = ?
                """, driverId);
        assertThat(jdbc.queryForObject("""
                select octet_length(avatar_data) from vehicle_tracking.drivers where id = ?
                """, Integer.class, driverId)).isEqualTo(3);

        var latest = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .defaultSchema("vehicle_tracking")
                .schemas("vehicle_tracking")
                .target("24")
                .load();
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);

        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = 'vehicle_tracking' and table_name = 'drivers'
                  and column_name in ('avatar_data', 'avatar_content_type')
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForMap("""
                select id, full_name, phone_number, license_number, active, created_at, updated_at
                from vehicle_tracking.drivers where id = ?
                """, driverId)).isEqualTo(profileBefore);
        assertThat(jdbc.queryForObject("""
                select count(*) from vehicle_tracking.flyway_schema_history
                where version in ('23', '24') and success = true
                """, Integer.class)).isEqualTo(2);
        assertThat(latest.migrate().migrationsExecuted).isZero();
    }
}
