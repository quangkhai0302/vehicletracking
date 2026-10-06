package com.quangkhai.vehicletracking_backend.reroute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RouteComparisonMigrationIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Test void v35PreservesHistoricalRowsAndConstrainsSnapshotsWithoutGuessingHistory() {
        flyway("34").migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()));
        Long vehicle = jdbc.queryForObject("""
                INSERT INTO vehicle_tracking.vehicles(plate_number,name,created_at,updated_at)
                VALUES ('COMPARE35','Compare fixture',now(),now()) RETURNING id
                """,Long.class);
        Long route = jdbc.queryForObject("""
                INSERT INTO vehicle_tracking.routes(name,transport_mode,routing_provider,total_distance_meters,
                estimated_travel_duration_seconds,base_travel_duration_seconds,total_dwell_duration_seconds,
                estimated_trip_duration_seconds,estimated_departure_at,calculated_at)
                VALUES ('Compare fixture','CAR','HERE',100,20,20,0,20,now(),now()) RETURNING id
                """,Long.class);
        Long trip = jdbc.queryForObject("""
                INSERT INTO vehicle_tracking.trips(vehicle_id,route_id,vehicle_plate_snapshot,scheduled_departure_at,created_at)
                VALUES (?,?,'COMPARE35',now(),now()) RETURNING id
                """,Long.class,vehicle,route);
        Long revision = jdbc.queryForObject("""
                INSERT INTO vehicle_tracking.trip_route_revisions(trip_id,source_route_id,revision_number,
                reason_code,reason_detail,severity,baseline_remaining_seconds,revised_remaining_seconds,created_at,activated_at)
                VALUES (?,?,1,'TRAFFIC_DELAY','Historical fixture','MAJOR',40,20,now(),now()) RETURNING id
                """,Long.class,trip,route);
        var original = jdbc.queryForMap("SELECT * FROM vehicle_tracking.trip_route_revisions WHERE id=?",revision);
        assertThat(flyway("35").migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT * FROM vehicle_tracking.trip_route_revisions WHERE id=?",revision))
                .containsAllEntriesOf(original).containsEntry("comparison_snapshot",null);

        String valid = """
                {"attemptNumber":1,"previousRevisionId":null,"anchor":{"latitude":10.77,"longitude":106.7},
                "before":{"encodedPolylines":["BFfixture"],"distanceMeters":100,"durationSeconds":40},
                "after":{"encodedPolylines":["BFfixture"],"distanceMeters":80,"durationSeconds":null}}
                """;
        assertThat(jdbc.update("UPDATE vehicle_tracking.trip_route_revisions SET comparison_snapshot=?::jsonb WHERE id=?",valid,revision)).isEqualTo(1);
        for (String invalid : List.of("{}","[]",valid.replace("\"attemptNumber\":1","\"attemptNumber\":0"),
                valid.replace("\"attemptNumber\":1","\"attemptNumber\":1.5"),
                valid.replace("\"previousRevisionId\":null","\"previousRevisionId\":1.5"),
                valid.replace("\"distanceMeters\":100","\"distanceMeters\":1.5"),
                valid.replace("\"durationSeconds\":40","\"durationSeconds\":1.5"),
                valid.replace("\"latitude\":10.77","\"latitude\":91"),
                valid.replace("\"longitude\":106.7","\"longitude\":181"),
                valid.replace("\"distanceMeters\":100","\"distanceMeters\":-1"),
                valid.replace("\"durationSeconds\":40","\"durationSeconds\":-1"),
                valid.replace("\"encodedPolylines\":[\"BFfixture\"]","\"encodedPolylines\":[]"),
                valid.replace("\"encodedPolylines\":[\"BFfixture\"]","\"encodedPolylines\":[null]"),
                valid.replace("\"encodedPolylines\":[\"BFfixture\"]","\"encodedPolylines\":[123]"),
                valid.replace("\"encodedPolylines\":[\"BFfixture\"]","\"encodedPolylines\":[\"\"]"),
                valid.replace("\"previousRevisionId\":null,",""))) {
            assertThatThrownBy(() -> jdbc.update("UPDATE vehicle_tracking.trip_route_revisions SET comparison_snapshot=?::jsonb WHERE id=?",invalid,revision))
                    .as("Malformed comparison snapshot must be rejected: %s",invalid).isInstanceOf(DataAccessException.class);
        }
        assertThat(jdbc.update("UPDATE vehicle_tracking.trip_route_revisions SET comparison_snapshot=NULL WHERE id=?",revision)).isEqualTo(1);
        assertThat(flyway("35").migrate().migrationsExecuted).isZero();
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword())
                .defaultSchema("vehicle_tracking").schemas("vehicle_tracking").target(target).load();
    }
}
