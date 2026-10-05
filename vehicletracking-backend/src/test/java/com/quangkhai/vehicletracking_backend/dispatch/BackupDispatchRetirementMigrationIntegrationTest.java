package com.quangkhai.vehicletracking_backend.dispatch;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class BackupDispatchRetirementMigrationIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Test void upgradeCancelsPendingOffersKeepsHistoryAndPreservesReadyAssignments() {
        flyway("32").migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        Long vehicle = jdbc.queryForObject("""
                insert into vehicle_tracking.vehicles(plate_number,name,created_at,updated_at)
                values ('RETIRE50','Xe fixture',now(),now()) returning id
                """, Long.class);
        Long primary = driver(jdbc, "PRIMARY50", "0900000050");
        Long candidate = driver(jdbc, "CANDIDATE50", "0900000051");
        Long route = jdbc.queryForObject("""
                insert into vehicle_tracking.routes(name,transport_mode,routing_provider,total_distance_meters,
                    estimated_travel_duration_seconds,base_travel_duration_seconds,total_dwell_duration_seconds,
                    estimated_trip_duration_seconds,estimated_departure_at,calculated_at)
                values ('Tuyến fixture','CAR','HERE',1,1,1,0,1,now(),now()) returning id
                """, Long.class);
        Long schedule = jdbc.queryForObject("""
                insert into vehicle_tracking.trip_schedules(route_id,vehicle_id,driver_id,frequency,scheduled_date,
                    departure_time,timezone,effective_from,created_at,updated_at,start_mode,backup_enabled)
                values (?,?,?,'ONCE','2026-10-01','08:00','UTC','2026-10-01',now(),now(),'AUTO_IF_READY',true)
                returning id
                """, Long.class, route, vehicle, primary);
        jdbc.update("insert into vehicle_tracking.schedule_backup_drivers values (?,?,1)", schedule, candidate);
        Long unassigned = trip(jdbc, route, vehicle, schedule, null, 0);
        Long assigned = trip(jdbc, route, vehicle, schedule, primary, 1);
        Long ready = trip(jdbc, route, vehicle, schedule, primary, 2);
        dispatch(jdbc, unassigned, primary, "OFFER_PENDING", null);
        dispatch(jdbc, assigned, primary, "SEARCH_WAIT", null);
        dispatch(jdbc, ready, primary, "WAITING_READY", null);
        jdbc.update("""
                update vehicle_tracking.trip_dispatches set state='READY', ready_driver_id=?, ready_vehicle_id=?,
                    ready_attempt_number=1, ready_assignment_revision=0, ready_at=now() where trip_id=?
                """, primary, vehicle, ready);
        for (long trip : new long[]{unassigned, assigned, ready}) {
            jdbc.update("insert into vehicle_tracking.trip_dispatch_candidates values (?,?,1)", trip, candidate);
            jdbc.update("""
                    insert into vehicle_tracking.trip_dispatch_events(trip_id,revision,kind,actor_kind,created_at)
                    values (?,0,'CREATED','SYSTEM',now())
                    """, trip);
        }
        UUID pending = UUID.randomUUID();
        UUID accepted = UUID.randomUUID();
        offer(jdbc, pending, unassigned, candidate, "PENDING");
        offer(jdbc, accepted, ready, candidate, "ACCEPTED");
        var historyBefore = jdbc.queryForMap("select * from vehicle_tracking.trip_dispatch_offers where id=?", accepted);
        var readyBefore = jdbc.queryForMap("""
                select ready_driver_id,ready_vehicle_id,ready_attempt_number,ready_assignment_revision,ready_at
                from vehicle_tracking.trip_dispatches where trip_id=?
                """, ready);

        var latest = flyway("33");
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForMap("select status,response_reason from vehicle_tracking.trip_dispatch_offers where id=?", pending))
                .containsEntry("status", "CANCELLED");
        assertThat(jdbc.queryForObject("select responded_at is not null from vehicle_tracking.trip_dispatch_offers where id=?", Boolean.class, pending)).isTrue();
        assertThat(jdbc.queryForMap("select * from vehicle_tracking.trip_dispatch_offers where id=?", accepted)).isEqualTo(historyBefore);
        assertThat(jdbc.queryForMap("select state,attention_code,next_action_at from vehicle_tracking.trip_dispatches where trip_id=?", unassigned))
                .containsEntry("state", "ATTENTION").containsEntry("attention_code", "NO_DRIVER").containsEntry("next_action_at", null);
        assertThat(jdbc.queryForObject("select state from vehicle_tracking.trip_dispatches where trip_id=?", String.class, assigned)).isEqualTo("WAITING_READY");
        assertThat(jdbc.queryForObject("select driver_id from vehicle_tracking.trips where id=?", Long.class, assigned)).isEqualTo(primary);
        assertThat(jdbc.queryForObject("select state from vehicle_tracking.trip_dispatches where trip_id=?", String.class, ready)).isEqualTo("READY");
        assertThat(jdbc.queryForMap("""
                select ready_driver_id,ready_vehicle_id,ready_attempt_number,ready_assignment_revision,ready_at
                from vehicle_tracking.trip_dispatches where trip_id=?
                """, ready)).isEqualTo(readyBefore);
        assertThat(jdbc.queryForObject("select count(*) from vehicle_tracking.schedule_backup_drivers", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from vehicle_tracking.trip_dispatch_candidates", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from vehicle_tracking.trip_dispatch_events where kind='CREATED'", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from vehicle_tracking.trip_dispatch_events where kind='POLICY_CHANGED'", Integer.class)).isEqualTo(3);
        assertThatThrownBy(() -> jdbc.update("update vehicle_tracking.trip_schedules set backup_enabled=true where id=?", schedule)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update vehicle_tracking.trip_dispatches set backup_enabled=true where trip_id=?", assigned)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update vehicle_tracking.trip_dispatches set state='SEARCH_WAIT' where trip_id=?", assigned)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(latest.migrate().migrationsExecuted).isZero();
    }

    private Long driver(JdbcTemplate jdbc, String license, String phone) {
        return jdbc.queryForObject("""
                insert into vehicle_tracking.drivers(full_name,phone_number,license_number,created_at,updated_at)
                values ('Tài xế fixture',?,?,now(),now()) returning id
                """, Long.class, phone, license);
    }
    private Long trip(JdbcTemplate jdbc, long route, long vehicle, long schedule, Long driver, int offset) {
        return jdbc.queryForObject("""
                insert into vehicle_tracking.trips(vehicle_id,route_id,vehicle_plate_snapshot,scheduled_departure_at,
                    created_at,schedule_id,schedule_occurrence_at,driver_id,driver_name_snapshot,driver_phone_snapshot,driver_license_number_snapshot)
                values (?,?,'RETIRE50','2026-10-01T08:00:00Z'::timestamptz + ? * interval '1 second',now(),?,
                    '2026-10-01T08:00:00Z'::timestamptz + ? * interval '1 second',?,
                    case when ?::bigint is null then null else 'Tài xế fixture' end,
                    case when ?::bigint is null then null else '0900000050' end,
                    case when ?::bigint is null then null else 'PRIMARY50' end) returning id
                """, Long.class, vehicle, route, offset, schedule, offset, driver, driver, driver, driver);
    }
    private void dispatch(JdbcTemplate jdbc, long trip, long primary, String state, String attention) {
        jdbc.update("""
                insert into vehicle_tracking.trip_dispatches(trip_id,start_mode,state,backup_enabled,primary_driver_id,
                    schedule_epoch,baseline_duration_seconds,attention_code,next_action_at,created_at,updated_at)
                values (?,'AUTO_IF_READY',?,true,?,0,60,?,now(),now(),now())
                """, trip, state, primary, attention);
    }
    private void offer(JdbcTemplate jdbc, UUID id, long trip, long candidate, String status) {
        jdbc.update("""
                insert into vehicle_tracking.trip_dispatch_offers(id,trip_id,candidate_driver_id,priority,status,
                    dispatch_revision,offered_at,expires_at) values (?,?,?,1,?,0,now(),now()+interval '2 minutes')
                """, id, trip, candidate, status);
    }
    private Flyway flyway(String target) {
        var config = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .defaultSchema("vehicle_tracking").schemas("vehicle_tracking");
        if (target != null) config.target(target);
        return config.load();
    }
}
