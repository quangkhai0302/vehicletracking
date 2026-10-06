package com.quangkhai.vehicletracking_backend.simulation.entity;
import java.time.Instant;

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
@Entity @Table(name="simulation_runs", schema="vehicle_tracking")
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class SimulationRunEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="trip_id", nullable=false, unique=true) private Long tripId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private SimulationStatus status=SimulationStatus.PAUSED;
    @Column(nullable=false) private int multiplier=1;
    @Column(name="elapsed_seconds",nullable=false) private double elapsedSeconds;
    @Column(name="last_tick_at",nullable=false) private Instant lastTickAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="error_message",length=255) private String errorMessage;
    @Column(name="replacement_trip_id") private Long replacementTripId;
    @Column(name="virtual_elapsed_seconds") private Double virtualElapsedSeconds;
    @Embedded private SimulationAttemptMetadata metadata;
    @Enumerated(EnumType.STRING) @Column(length=20) private SimulationScenario scenario=SimulationScenario.CURRENT_TRAFFIC;
    public SimulationRunEntity(long tripId, Instant now) { this.tripId=tripId; createdAt=now; updatedAt=now; lastTickAt=now; }
    public void advance(double elapsed, Instant now) { elapsedSeconds=elapsed; lastTickAt=now; updatedAt=now; }
    public void addVirtualSeconds(double delta) {
        if (!Double.isFinite(delta) || delta<0) throw new IllegalArgumentException("Invalid virtual delta");
        if (virtualElapsedSeconds!=null) virtualElapsedSeconds+=delta;
    }
    public void captureFirstPlay(SimulationAttemptMetadata snapshot) {
        if (metadata==null && elapsedSeconds==0 && virtualElapsedSeconds==null) {
            metadata=snapshot; virtualElapsedSeconds=0d;
        }
    }
    public void changeScenario(SimulationScenario value) { scenario=java.util.Objects.requireNonNull(value); }
    public void changeScenario(SimulationScenario value, Instant now) { changeScenario(value); updatedAt=now; lastTickAt=now; }
    public SimulationScenario getScenario() { return scenario==null?SimulationScenario.CURRENT_TRAFFIC:scenario; }
    public void changeStatus(SimulationStatus status, Instant now) { this.status=status; lastTickAt=now; updatedAt=now; }
    public void changeMultiplier(int value, Instant now) { multiplier=value; lastTickAt=now; updatedAt=now; }
    public void fail(String message, Instant now) { changeStatus(SimulationStatus.FAILED,now); errorMessage=message; }
    public void replaceWith(long tripId, Instant now) { replacementTripId=tripId; updatedAt=now; }
    public void replay(Instant now) {
        elapsedSeconds=0; multiplier=1; errorMessage=null; replacementTripId=null;
        virtualElapsedSeconds=null; metadata=null; scenario=SimulationScenario.CURRENT_TRAFFIC;
        changeStatus(SimulationStatus.PAUSED,now);
    }
}
