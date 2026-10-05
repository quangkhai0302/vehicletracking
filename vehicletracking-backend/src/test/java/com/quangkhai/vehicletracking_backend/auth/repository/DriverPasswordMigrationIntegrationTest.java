package com.quangkhai.vehicletracking_backend.auth.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class DriverPasswordMigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Test
    void v32PreservesExistingCredentialsAndLimitsRequiredChangeToDrivers() {
        flyway("31").migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        Long driver = jdbc.queryForObject("""
                insert into vehicle_tracking.drivers(full_name,phone_number,license_number,active,created_at,updated_at)
                values ('Migration driver','0901234567','PASSWORD-MIGRATION',true,now(),now()) returning id
                """, Long.class);
        Long driverAccount = jdbc.queryForObject("""
                insert into vehicle_tracking.user_accounts(username,password_hash,role,driver_id,active,created_at,updated_at)
                values ('legacy.driver','fixture-driver-hash','DRIVER',?,true,now(),now()) returning id
                """, Long.class, driver);
        Long adminAccount = jdbc.queryForObject("""
                insert into vehicle_tracking.user_accounts(username,password_hash,role,active,created_at,updated_at)
                values ('legacy.admin','fixture-admin-hash','ADMIN',true,now(),now()) returning id
                """, Long.class);
        var before = jdbc.queryForList("select * from vehicle_tracking.user_accounts order by id");

        var migration = flyway("32");
        assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);
        var after = jdbc.queryForList("select * from vehicle_tracking.user_accounts order by id");
        assertThat(after).hasSize(before.size());
        for (int index = 0; index < before.size(); index++) {
            assertThat(after.get(index)).containsAllEntriesOf(before.get(index))
                    .containsEntry("password_change_required", false);
        }

        assertThat(jdbc.update("update vehicle_tracking.user_accounts set password_change_required=true where id=?", driverAccount))
                .isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update(
                "update vehicle_tracking.user_accounts set password_change_required=true where id=?", adminAccount))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "update vehicle_tracking.user_accounts set password_change_required=null where id=?", driverAccount))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("update vehicle_tracking.user_accounts set password_change_required=false where id=?", driverAccount);
        assertThat(migration.migrate().migrationsExecuted).isZero();
    }

    private Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .defaultSchema("vehicle_tracking").schemas("vehicle_tracking")
                .target(target).load();
    }
}
