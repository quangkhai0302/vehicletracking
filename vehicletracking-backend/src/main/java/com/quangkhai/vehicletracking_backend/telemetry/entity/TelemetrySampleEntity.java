package com.quangkhai.vehicletracking_backend.telemetry.entity;

import com.quangkhai.vehicletracking_backend.telemetry.dto.TelemetryRequest;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="telemetry_samples", schema="vehicle_tracking")
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class TelemetrySampleEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="event_id", nullable=false, unique=true) private UUID eventId;
    @Column(name="vehicle_id", nullable=false) private Long vehicleId;
    @Column(name="trip_id", nullable=false) private Long tripId;
    @Column(name="attempt_number", nullable=false) private int attemptNumber = 1;
    public void assignAttempt(int attemptNumber) { this.attemptNumber = attemptNumber; }
    @Column(name="recorded_at", nullable=false) private Instant recordedAt;
    @Column(name="received_at", nullable=false) private Instant receivedAt;
    @Column(name="simulated_at") private Instant simulatedAt;
    @Column(nullable=false) private double latitude;
    @Column(nullable=false) private double longitude;
    @Column(name="speed_kmh", nullable=false) private double speedKmh;
    @Column(nullable=false) private double heading;
    @Column(name="accuracy_meters", nullable=false) private double accuracyMeters;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private TelemetrySource source;
    public TelemetrySampleEntity(TelemetryRequest input, Instant receivedAt, Instant simulatedAt) {
        eventId=input.eventId(); vehicleId=input.vehicleId(); tripId=input.tripId();
        recordedAt=input.recordedAt(); this.receivedAt=receivedAt; this.simulatedAt=simulatedAt;
        latitude=input.latitude(); longitude=input.longitude(); speedKmh=input.speedKmh();
        heading=input.heading(); accuracyMeters=input.accuracyMeters(); source=input.source();
    }
    public boolean matches(TelemetryRequest input) {
        return vehicleId.equals(input.vehicleId()) && tripId.equals(input.tripId()) && recordedAt.equals(input.recordedAt())
                && Double.compare(latitude,input.latitude())==0 && Double.compare(longitude,input.longitude())==0
                && Double.compare(speedKmh,input.speedKmh())==0 && Double.compare(heading,input.heading())==0
                && Double.compare(accuracyMeters,input.accuracyMeters())==0 && source==input.source();
    }
}
