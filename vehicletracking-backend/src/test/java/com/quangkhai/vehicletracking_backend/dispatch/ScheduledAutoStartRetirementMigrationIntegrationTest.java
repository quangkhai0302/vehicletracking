package com.quangkhai.vehicletracking_backend.dispatch;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class ScheduledAutoStartRetirementMigrationIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Test void upgradeArchivesAllModesPreservesReadyAndTripHistoryAndCannotReactivate() {
        flyway("33").migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        long vehicle = jdbc.queryForObject("""
                insert into vehicle_tracking.vehicles(plate_number,name,created_at,updated_at)
                values ('RETIRE52','Xe fixture',now(),now()) returning id
                """, Long.class);
        long driver = jdbc.queryForObject("""
                insert into vehicle_tracking.drivers(full_name,phone_number,license_number,created_at,updated_at)
                values ('Tài xế fixture','0900000052','LICENSE52',now(),now()) returning id
                """, Long.class);
        long route = jdbc.queryForObject("""
                insert into vehicle_tracking.routes(name,transport_mode,routing_provider,total_distance_meters,
                    estimated_travel_duration_seconds,base_travel_duration_seconds,total_dwell_duration_seconds,
                    estimated_trip_duration_seconds,estimated_departure_at,calculated_at)
                values ('Tuyến fixture','CAR','HERE',1,1,1,0,1,now(),now()) returning id
                """, Long.class);
        long schedule = jdbc.queryForObject("""
                insert into vehicle_tracking.trip_schedules(route_id,vehicle_id,driver_id,frequency,scheduled_date,
                    departure_time,timezone,effective_from,created_at,updated_at,start_mode)
                values (?,?,?,'ONCE','2026-10-01','08:00','UTC','2026-10-01',now(),now(),'AUTO_IF_READY') returning id
                """, Long.class, route, vehicle, driver);
        int offset = 0;
        long readyTrip = 0;
        for (String state : List.of("MANUAL", "WAITING_READY", "READY", "ATTENTION", "STARTED", "CLOSED")) {
            long trip = jdbc.queryForObject("""
                    insert into vehicle_tracking.trips(vehicle_id,route_id,vehicle_plate_snapshot,scheduled_departure_at,
                        created_at,schedule_id,schedule_occurrence_at,driver_id,driver_name_snapshot,driver_phone_snapshot,
                        driver_license_number_snapshot)
                    values (?,?,'RETIRE52','2026-10-01T08:00:00Z'::timestamptz + ? * interval '1 second',now(),?,
                        '2026-10-01T08:00:00Z'::timestamptz + ? * interval '1 second',?,'Tài xế fixture','0900000052','LICENSE52')
                    returning id
                    """, Long.class, vehicle, route, offset, schedule, offset++, driver);
            jdbc.update("""
                    insert into vehicle_tracking.trip_dispatches(trip_id,start_mode,state,primary_driver_id,
                        schedule_epoch,baseline_duration_seconds,created_at,updated_at,next_action_at,attention_code,
                        ready_driver_id,ready_vehicle_id,ready_attempt_number,ready_assignment_revision,ready_at)
                    values (?,?,?,?,0,60,now(),now(),?,?,?, ?, ?, ?, ?)
                    """, trip, state.equals("MANUAL") ? "MANUAL" : "AUTO_IF_READY", state, driver,
                    state.equals("MANUAL") || state.equals("CLOSED") ? null : java.sql.Timestamp.from(java.time.Instant.EPOCH),
                    state.equals("ATTENTION") ? "DRIVER_NOT_READY" : null,
                    state.equals("READY") ? driver : null, state.equals("READY") ? vehicle : null,
                    state.equals("READY") ? 1 : null, state.equals("READY") ? 0 : null,
                    state.equals("READY") ? java.sql.Timestamp.from(java.time.Instant.EPOCH) : null);
            jdbc.update("""
                    insert into vehicle_tracking.trip_dispatch_events(trip_id,revision,kind,actor_kind,created_at)
                    values (?,0,'CREATED','SYSTEM',now())
                    """, trip);
            if (state.equals("READY")) readyTrip = trip;
            if (state.equals("STARTED")) jdbc.update("update vehicle_tracking.trips set status='IN_PROGRESS',started_at=now() where id=?", trip);
        }
        var tripsBefore = jdbc.queryForList("select * from vehicle_tracking.trips order by id");
        var readyBefore = jdbc.queryForMap("""
                select ready_driver_id,ready_vehicle_id,ready_attempt_number,ready_assignment_revision,ready_at,created_at,start_mode
                from vehicle_tracking.trip_dispatches where trip_id=?
                """, readyTrip);
        var latest = flyway(null);
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForList("select * from vehicle_tracking.trips order by id")).isEqualTo(tripsBefore);
        assertThat(jdbc.queryForMap("""
                select ready_driver_id,ready_vehicle_id,ready_attempt_number,ready_assignment_revision,ready_at,created_at,start_mode
                from vehicle_tracking.trip_dispatches where trip_id=?
                """, readyTrip)).isEqualTo(readyBefore);
        assertThat(jdbc.queryForObject("select start_mode from vehicle_tracking.trip_schedules where id=?", String.class, schedule)).isEqualTo("MANUAL");
        assertThat(jdbc.queryForObject("select version from vehicle_tracking.trip_schedules where id=?", Long.class, schedule)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from vehicle_tracking.trip_dispatches where state='CLOSED' and next_action_at is null and attention_code is null", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("select count(*) from vehicle_tracking.trip_dispatch_events where kind='CREATED'", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("select count(*) from vehicle_tracking.trip_dispatch_events where kind='CLOSED'", Integer.class)).isEqualTo(5);
        assertThatThrownBy(() -> jdbc.update("update vehicle_tracking.trip_schedules set start_mode='AUTO_IF_READY' where id=?", schedule))
                .isInstanceOf(DataIntegrityViolationException.class);
        long retainedTrip = readyTrip;
        assertThatThrownBy(() -> jdbc.update("update vehicle_tracking.trip_dispatches set state='READY',next_action_at=now() where trip_id=?", retainedTrip))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(latest.migrate().migrationsExecuted).isZero();
    }

    private Flyway flyway(String target) {
        var config = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .defaultSchema("vehicle_tracking").schemas("vehicle_tracking");
        if (target != null) config.target(target);
        return config.load();
    }
}
