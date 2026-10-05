package com.quangkhai.vehicletracking_backend.depot;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class DepotReturnMigrationIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");
    @Test void upgradesV24WithoutBackfillAndProtectsReturnEvidence() {
        var before = flyway("24"); before.migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        Long actor = jdbc.queryForObject("insert into vehicle_tracking.user_accounts(username,password_hash,role,active,created_at,updated_at) values ('migration-admin','fixture-hash','ADMIN',true,now(),now()) returning id", Long.class);
        Long vehicle = jdbc.queryForObject("insert into vehicle_tracking.vehicles(plate_number,name,created_at,updated_at) values ('MIGRATE46','Xe fixture',now(),now()) returning id", Long.class);
        Long route = jdbc.queryForObject("""
                insert into vehicle_tracking.routes(name,transport_mode,routing_provider,total_distance_meters,
                    estimated_travel_duration_seconds,base_travel_duration_seconds,total_dwell_duration_seconds,
                    estimated_trip_duration_seconds,estimated_departure_at,calculated_at)
                values ('Tuyến fixture','CAR','HERE',1,1,1,0,1,now(),now()) returning id
                """, Long.class);
        Long trip = jdbc.queryForObject("insert into vehicle_tracking.trips(vehicle_id,route_id,vehicle_plate_snapshot,scheduled_departure_at,created_at) values (?,?,'MIGRATE46',now(),now()) returning id", Long.class, vehicle, route);
        var tripBefore = jdbc.queryForMap("select * from vehicle_tracking.trips where id=?", trip);
        var latest = flyway("30");
        // V25–V30 are the post-V24 depot/turnaround/readiness migrations.
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(6);
        var tripAfter = jdbc.queryForMap("select * from vehicle_tracking.trips where id=?", trip);
        assertThat(tripAfter).containsAllEntriesOf(tripBefore)
                .containsEntry("turnaround_legacy_max_attempt", tripBefore.get("attempt_number"));
        assertThat(jdbc.queryForObject("select count(*) from vehicle_tracking.operating_depot", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from vehicle_tracking.depot_return_records", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from vehicle_tracking.trip_turnaround_plans", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from vehicle_tracking.resource_depot_confirmations", Long.class)).isZero();
        assertThatThrownBy(() -> jdbc.update("insert into vehicle_tracking.operating_depot(id,name,address,created_at,updated_at,updated_by_account_id) values (2,'Bãi','Địa chỉ',now(),now(),?)", actor)).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("insert into vehicle_tracking.operating_depot(id,name,address,created_at,updated_at,updated_by_account_id) values (1,'Bãi','Địa chỉ',now(),now(),?)", actor);
        jdbc.update("""
                insert into vehicle_tracking.depot_return_records(trip_id,attempt_number,depot_id,depot_revision,
                    depot_name_snapshot,depot_address_snapshot,mode,planned_return_at,created_at,updated_at,updated_by_account_id)
                values (?,1,1,0,'Bãi','Địa chỉ','PLANNED',now(),now(),now(),?)
                """, trip, actor);
        assertThatThrownBy(() -> jdbc.update("insert into vehicle_tracking.depot_return_records(trip_id,attempt_number,depot_id,depot_revision,depot_name_snapshot,depot_address_snapshot,mode,planned_return_at,created_at,updated_at,updated_by_account_id) values (?,1,1,0,'Bãi','Địa chỉ','PLANNED',now(),now(),now(),?)", trip, actor)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update vehicle_tracking.depot_return_records set planned_return_at=null where trip_id=?", trip)).isInstanceOf(DataIntegrityViolationException.class);
        // V27 rejects new exemptions in a trigger before V25's CHECK runs.
        assertThatThrownBy(() -> jdbc.update("update vehicle_tracking.depot_return_records set mode='NOT_REQUIRED', planned_return_at=null,reason='Không cần về bãi' where trip_id=?", trip))
                .isInstanceOf(UncategorizedSQLException.class)
                .hasMessageContaining("NOT_REQUIRED is legacy-only");
        assertThatThrownBy(() -> jdbc.update("update vehicle_tracking.depot_return_records set mode='RETURNED', returned_at=now(),confirmed_at=now(),confirmed_by_account_id=?,confirmed_vehicle_id=?,vehicle_plate_snapshot='MIGRATE46' where trip_id=?", actor, vehicle, trip)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update vehicle_tracking.depot_return_records set mode='RETURNED',returned_at=now()+interval '1 second',confirmed_at=now(),source='ADMIN_MANUAL',confirmed_by_account_id=?,confirmed_vehicle_id=?,vehicle_plate_snapshot='MIGRATE46' where trip_id=?", actor, vehicle, trip)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("delete from vehicle_tracking.trips where id=?", trip)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("delete from vehicle_tracking.operating_depot where id=1")).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(latest.migrate().migrationsExecuted).isZero();
        var depotBefore = jdbc.queryForMap("select * from vehicle_tracking.operating_depot where id=1");
        var returnBefore = jdbc.queryForMap("select * from vehicle_tracking.depot_return_records where trip_id=?", trip);
        var locationMigration = flyway("31");
        assertThat(locationMigration.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForMap("select * from vehicle_tracking.operating_depot where id=1"))
                .containsAllEntriesOf(depotBefore).containsEntry("latitude", null).containsEntry("longitude", null);
        assertThat(jdbc.queryForMap("select * from vehicle_tracking.depot_return_records where trip_id=?", trip))
                .isEqualTo(returnBefore);
        assertThatThrownBy(() -> jdbc.update("update vehicle_tracking.operating_depot set latitude=10 where id=1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update vehicle_tracking.operating_depot set longitude=106 where id=1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        for (String invalid : java.util.List.of("91,0", "0,-181", "'NaN',0")) {
            var pair = invalid.split(",");
            assertThatThrownBy(() -> jdbc.update("update vehicle_tracking.operating_depot set latitude=" + pair[0]
                    + ", longitude=" + pair[1] + " where id=1")).isInstanceOf(DataIntegrityViolationException.class);
        }
        jdbc.update("update vehicle_tracking.operating_depot set latitude=-90,longitude=180 where id=1");
        assertThat(jdbc.queryForObject("select longitude from vehicle_tracking.operating_depot where id=1", java.math.BigDecimal.class))
                .isEqualByComparingTo("180");
        assertThat(locationMigration.migrate().migrationsExecuted).isZero();
    }
    private Flyway flyway(String target) {
        var configuration = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .defaultSchema("vehicle_tracking").schemas("vehicle_tracking");
        if (target != null) configuration.target(target);
        return configuration.load();
    }
}
