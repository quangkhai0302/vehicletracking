package com.quangkhai.vehicletracking_backend.simulation.entity;

import java.time.Instant;

import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Immutable lifecycle snapshot; samples and visits are retained by trip + attempt. */
@Entity @Table(name="simulation_attempts", schema="vehicle_tracking")
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class SimulationAttemptEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="trip_id", nullable=false) private long tripId;
    @Column(name="attempt_number", nullable=false) private int attemptNumber;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private SimulationStatus status;
    @Enumerated(EnumType.STRING) @Column(name="trip_status", nullable=false, length=20) private TripStatus tripStatus;
    @Column(name="elapsed_seconds", nullable=false) private double elapsedSeconds;
    @Column(nullable=false) private int multiplier;
    @Column(name="scheduled_departure_at", nullable=false) private Instant scheduledDepartureAt;
    @Column(name="started_at") private Instant startedAt;
    @Column(name="ended_at") private Instant endedAt;
    @Column(name="archived_at", nullable=false) private Instant archivedAt;
    @Column(name="error_message") private String errorMessage;
    @Column(name="virtual_elapsed_seconds") private Double virtualElapsedSeconds;
    @Embedded private SimulationAttemptMetadata metadata;
    @Enumerated(EnumType.STRING) @Column(length=20) private SimulationScenario scenario;
    public SimulationAttemptEntity(TripEntity trip, SimulationRunEntity run, Instant now) {
        tripId=trip.getId(); attemptNumber=trip.getAttemptNumber(); status=run.getStatus(); tripStatus=trip.getStatus();
        elapsedSeconds=run.getElapsedSeconds(); multiplier=run.getMultiplier(); scheduledDepartureAt=trip.getScheduledDepartureAt();
        startedAt=trip.getStartedAt(); endedAt=trip.getEndedAt(); archivedAt=now; errorMessage=run.getErrorMessage();
        virtualElapsedSeconds=run.getVirtualElapsedSeconds(); metadata=run.getMetadata()==null?null:run.getMetadata().copy();
        scenario=run.getScenario();
    }
}
