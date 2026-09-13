package com.quangkhai.vehicletracking_backend.simulation.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
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
    public SimulationRunEntity(long tripId, Instant now) { this.tripId=tripId; createdAt=now; updatedAt=now; lastTickAt=now; }
    public void advance(double elapsed, Instant now) { elapsedSeconds=elapsed; lastTickAt=now; updatedAt=now; }
    public void changeStatus(SimulationStatus status, Instant now) { this.status=status; lastTickAt=now; updatedAt=now; }
    public void changeMultiplier(int value, Instant now) { multiplier=value; lastTickAt=now; updatedAt=now; }
    public void fail(String message, Instant now) { changeStatus(SimulationStatus.FAILED,now); errorMessage=message; }
    public void replaceWith(long tripId, Instant now) { replacementTripId=tripId; updatedAt=now; }
}
