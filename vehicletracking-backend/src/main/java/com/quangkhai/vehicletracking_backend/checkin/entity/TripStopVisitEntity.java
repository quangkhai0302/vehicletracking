package com.quangkhai.vehicletracking_backend.checkin.entity;

import com.quangkhai.vehicletracking_backend.telemetry.entity.TelemetrySampleEntity;
import com.quangkhai.vehicletracking_backend.telemetry.entity.TelemetrySource;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "trip_stop_visits", schema = "vehicle_tracking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripStopVisitEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "trip_id", nullable = false) private TripEntity trip;
    @Column(name = "stop_sequence", nullable = false) private Integer stopSequence;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private TelemetrySource source;
    @Enumerated(EnumType.STRING) @Column(name = "evidence_kind", nullable = false, length = 20) private CheckInEvidenceKind evidenceKind;
    @Column(name = "actual_arrival_at", nullable = false) private Instant actualArrivalAt;
    @Column(name = "simulated_arrival_at") private Instant simulatedArrivalAt;
    @Column(name = "detected_at", nullable = false) private Instant detectedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "from_sample_id") private TelemetrySampleEntity fromSample;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "to_sample_id", nullable = false) private TelemetrySampleEntity toSample;
    @Column(name = "evidence_fraction", nullable = false) private double evidenceFraction;
    @Column(nullable = false) private double latitude;
    @Column(nullable = false) private double longitude;

    public TripStopVisitEntity(TripEntity trip, int stopSequence, TelemetrySource source,
            CheckInEvidenceKind evidenceKind, Instant actualArrivalAt, Instant simulatedArrivalAt,
            Instant detectedAt, TelemetrySampleEntity fromSample, TelemetrySampleEntity toSample,
            double evidenceFraction, double latitude, double longitude) {
        this.trip = trip; this.stopSequence = stopSequence; this.source = source; this.evidenceKind = evidenceKind;
        this.actualArrivalAt = actualArrivalAt; this.simulatedArrivalAt = simulatedArrivalAt; this.detectedAt = detectedAt;
        this.fromSample = fromSample; this.toSample = toSample; this.evidenceFraction = evidenceFraction;
        this.latitude = latitude; this.longitude = longitude;
    }
}
