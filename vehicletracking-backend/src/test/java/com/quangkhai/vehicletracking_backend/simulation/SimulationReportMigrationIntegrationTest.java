package com.quangkhai.vehicletracking_backend.simulation;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DataAccessException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
@Testcontainers
class SimulationReportMigrationIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17");
    @Test void upgradesV35WithoutInventingLegacyClockOrIdentityAndOnlyMigratesCertainProvenance() {
        flyway("35").migrate();
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()));
        Long vehicle=jdbc.queryForObject("INSERT INTO vehicle_tracking.vehicles(plate_number,name,created_at,updated_at) VALUES ('REPORT36','Fixture',now(),now()) RETURNING id",Long.class);
        Long route=jdbc.queryForObject("""
            INSERT INTO vehicle_tracking.routes(name,transport_mode,routing_provider,total_distance_meters,
            estimated_travel_duration_seconds,base_travel_duration_seconds,total_dwell_duration_seconds,
            estimated_trip_duration_seconds,estimated_departure_at,calculated_at)
            VALUES ('Fixture','CAR','HERE',100,20,20,0,20,now(),now()) RETURNING id
            """,Long.class);
        Long trip=jdbc.queryForObject("""
            INSERT INTO vehicle_tracking.trips(vehicle_id,route_id,vehicle_plate_snapshot,scheduled_departure_at,created_at)
            VALUES (?,?,'REPORT36',now(),now()) RETURNING id
            """,Long.class,vehicle,route);
        jdbc.update("""
            INSERT INTO vehicle_tracking.simulation_runs(trip_id,status,multiplier,elapsed_seconds,last_tick_at,updated_at,created_at)
            VALUES (?,'PAUSED',5,12,now(),now(),now())
            """,trip);
        jdbc.update("""
            INSERT INTO vehicle_tracking.simulation_attempts(trip_id,attempt_number,status,trip_status,elapsed_seconds,
            multiplier,scheduled_departure_at,archived_at) VALUES (?,1,'COMPLETED','COMPLETED',20,5,now(),now())
            """,trip);
        Long revision=jdbc.queryForObject("""
            INSERT INTO vehicle_tracking.trip_route_revisions(trip_id,source_route_id,revision_number,reason_code,severity,
            baseline_remaining_seconds,revised_remaining_seconds,created_at,activated_at,simulation_start_elapsed,simulation_attempt_number)
            VALUES (?,?,1,'TRAFFIC_DELAY','MAJOR',20,15,now(),now(),5,1) RETURNING id
            """,Long.class,trip,route);
        jdbc.update("""
            INSERT INTO vehicle_tracking.trip_notifications(trip_id,revision_id,type,severity,title,reason,affected_stop_sequences,dedupe_key,created_at)
            VALUES (?,?,'REROUTE_CREATED','MAJOR','Fixture','Fixture','','certain',now()),
                   (?,NULL,'OFF_ROUTE_DETECTED','MAJOR','Fixture','Fixture','','unknown',now())
            """,trip,revision,trip);
        var originalRun=jdbc.queryForMap("SELECT * FROM vehicle_tracking.simulation_runs WHERE trip_id=?",trip);
        var originalAttempt=jdbc.queryForMap("SELECT * FROM vehicle_tracking.simulation_attempts WHERE trip_id=?",trip);
        assertThat(flyway("36").migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT * FROM vehicle_tracking.simulation_runs WHERE trip_id=?",trip)).containsAllEntriesOf(originalRun)
            .containsEntry("virtual_elapsed_seconds",null).containsEntry("planned_duration_seconds",null).containsEntry("attempt_started_at",null).containsEntry("report_vehicle_id",null);
        assertThat(jdbc.queryForMap("SELECT * FROM vehicle_tracking.simulation_attempts WHERE trip_id=?",trip)).containsAllEntriesOf(originalAttempt)
            .containsEntry("virtual_elapsed_seconds",null).containsEntry("report_driver_name",null);
        assertThat(jdbc.queryForMap("SELECT attempt_number,source FROM vehicle_tracking.trip_notifications WHERE dedupe_key='certain'"))
            .containsEntry("attempt_number",1).containsEntry("source","SIMULATOR");
        assertThat(jdbc.queryForMap("SELECT attempt_number,source FROM vehicle_tracking.trip_notifications WHERE dedupe_key='unknown'"))
            .containsEntry("attempt_number",null).containsEntry("source",null);
        for(String table:List.of("simulation_runs","simulation_attempts")) {
            for(String field:List.of("virtual_elapsed_seconds","planned_duration_seconds","planned_distance_meters")) {
                for(String value:List.of("-1","'Infinity'::double precision","'NaN'::double precision")) {
                    assertThatThrownBy(() -> jdbc.update("UPDATE vehicle_tracking."+table+" SET "+field+"="+value+" WHERE trip_id=?",trip)).isInstanceOf(DataAccessException.class);
                }
            }
            assertThatThrownBy(() -> jdbc.update("UPDATE vehicle_tracking."+table+" SET scenario='BAD' WHERE trip_id=?",trip)).isInstanceOf(DataAccessException.class);
        }
        assertThatThrownBy(() -> jdbc.update("UPDATE vehicle_tracking.trip_notifications SET attempt_number=0")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE vehicle_tracking.trip_notifications SET source='FAKE'")).isInstanceOf(DataAccessException.class);
        assertThat(flyway("36").migrate().migrationsExecuted).isZero();
    }
    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword())
            .defaultSchema("vehicle_tracking").schemas("vehicle_tracking").target(target).load();
    }
}

