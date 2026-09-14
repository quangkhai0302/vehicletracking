package com.quangkhai.vehicletracking_backend.reroute.entity;

import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

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

    @Column(name = "dedupe_key", nullable = false, unique = true, length = 255)
    private String dedupeKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    public TripNotificationEntity(TripEntity trip, TripRouteRevisionEntity revision, NotificationType type,
                                  NotificationSeverity severity, String title, String reason, String incidentId,
                                  String affectedStopSequences, Long baselineEtaSeconds, Long revisedEtaSeconds,
                                  String dedupeKey, Instant createdAt) {
        this.trip = trip;
        this.revision = revision;
        this.type = type;
        this.severity = severity;
        this.title = title;
        this.reason = reason;
        this.incidentId = incidentId;
        this.affectedStopSequences = affectedStopSequences;
        this.baselineEtaSeconds = baselineEtaSeconds;
        this.revisedEtaSeconds = revisedEtaSeconds;
        this.dedupeKey = dedupeKey;
        this.createdAt = createdAt;
    }

    public void markRead(Instant now) {
        this.readAt = now;
    }
}
