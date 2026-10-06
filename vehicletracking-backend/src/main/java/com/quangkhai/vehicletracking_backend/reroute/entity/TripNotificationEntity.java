package com.quangkhai.vehicletracking_backend.reroute.entity;

import java.time.Instant;

import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "trip_notifications", schema = "vehicle_tracking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripNotificationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private TripEntity trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revision_id")
    private TripRouteRevisionEntity revision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationSeverity severity;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(name = "incident_id", length = 150)
    private String incidentId;

    @Column(name = "affected_stop_sequences", nullable = false, length = 255)
    private String affectedStopSequences;

    @Column(name = "baseline_eta_seconds")
    private Long baselineEtaSeconds;

    @Column(name = "revised_eta_seconds")
    private Long revisedEtaSeconds;
    @Column(name = "measured_distance_meters") private Double measuredDistanceMeters;
    @Column(name = "threshold_distance_meters") private Double thresholdDistanceMeters;
    @Column(name = "breach_duration_seconds") private Long breachDurationSeconds;

    @Column(name = "dedupe_key", nullable = false, unique = true, length = 255)
    private String dedupeKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    /**
     * A notification may be removed from the operator inbox without erasing
     * the operational event used by historical reports.
     */
    @Column(name = "dismissed_at")
    private Instant dismissedAt;
    @Column(name="attempt_number") private Integer attemptNumber;
    @Enumerated(EnumType.STRING) @Column(length=20) private com.quangkhai.vehicletracking_backend.telemetry.entity.TelemetrySource source;

    public void attributeSimulation(int attempt) {
        if(attempt<1) throw new IllegalArgumentException("Invalid notification attempt");
        attemptNumber=attempt; source=com.quangkhai.vehicletracking_backend.telemetry.entity.TelemetrySource.SIMULATOR;
    }

    public TripNotificationEntity(TripEntity trip, TripRouteRevisionEntity revision, NotificationType type,
                                  NotificationSeverity severity, String title, String reason, String incidentId,
                                  String affectedStopSequences, Long baselineEtaSeconds, Long revisedEtaSeconds,
                                  String dedupeKey, Instant createdAt) {
        this(trip, revision, type, severity, title, reason, incidentId, affectedStopSequences,
                baselineEtaSeconds, revisedEtaSeconds, null, null, null, dedupeKey, createdAt);
    }

    public TripNotificationEntity(TripEntity trip, TripRouteRevisionEntity revision, NotificationType type,
                                  NotificationSeverity severity, String title, String reason, String incidentId,
                                  String affectedStopSequences, Long baselineEtaSeconds, Long revisedEtaSeconds,
                                  Double measuredDistanceMeters, Double thresholdDistanceMeters,
                                  Long breachDurationSeconds, String dedupeKey, Instant createdAt) {
        this.trip = trip;
        this.attemptNumber=trip.getAttemptNumber();
        if(revision!=null && revision.getSimulationAttemptNumber()!=null) attributeSimulation(revision.getSimulationAttemptNumber());
        this.revision = revision;
        this.type = type;
        this.severity = severity;
        this.title = title;
        this.reason = reason;
        this.incidentId = incidentId;
        this.affectedStopSequences = affectedStopSequences;
        this.baselineEtaSeconds = baselineEtaSeconds;
        this.revisedEtaSeconds = revisedEtaSeconds;
        this.measuredDistanceMeters = measuredDistanceMeters;
        this.thresholdDistanceMeters = thresholdDistanceMeters;
        this.breachDurationSeconds = breachDurationSeconds;
        this.dedupeKey = dedupeKey;
        this.createdAt = createdAt;
    }

    public void markRead(Instant now) {
        this.readAt = now;
    }

    public void dismiss(Instant now) {
        this.dismissedAt = now;
    }
}
