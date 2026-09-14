package com.quangkhai.vehicletracking_backend.reroute.entity;

import com.quangkhai.vehicletracking_backend.route.entity.RouteEntity;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "trip_route_revisions", schema = "vehicle_tracking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripRouteRevisionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private TripEntity trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_route_id", nullable = false)
    private RouteEntity sourceRoute;

    @Column(name = "revision_number", nullable = false)
    private Integer revisionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RouteRevisionStatus status = RouteRevisionStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 40)
    private RerouteReasonCode reasonCode;

    @Column(name = "reason_detail", length = 255)
    private String reasonDetail;

    @Column(name = "trigger_incident_id", length = 150)
    private String triggerIncidentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationSeverity severity;

    @Column(name = "baseline_remaining_seconds", nullable = false)
    private long baselineRemainingSeconds;

    @Column(name = "revised_remaining_seconds", nullable = false)
    private long revisedRemainingSeconds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "activated_at", nullable = false, updatable = false)
    private Instant activatedAt;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @OneToMany(mappedBy = "revision", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNumber ASC")
    private List<TripRouteRevisionStopEntity> stops = new ArrayList<>();

    @OneToMany(mappedBy = "revision", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sectionSequence ASC")
    private List<TripRouteRevisionSectionEntity> sections = new ArrayList<>();

    public TripRouteRevisionEntity(TripEntity trip, RouteEntity sourceRoute, int revisionNumber,
                                   RerouteReasonCode reasonCode, String reasonDetail, String triggerIncidentId,
                                   NotificationSeverity severity, long baselineRemainingSeconds,
                                   long revisedRemainingSeconds, Instant now) {
        this.trip = trip;
        this.sourceRoute = sourceRoute;
        this.revisionNumber = revisionNumber;
        this.reasonCode = reasonCode;
        this.reasonDetail = reasonDetail;
        this.triggerIncidentId = triggerIncidentId;
        this.severity = severity;
        this.baselineRemainingSeconds = Math.max(0, baselineRemainingSeconds);
        this.revisedRemainingSeconds = Math.max(0, revisedRemainingSeconds);
        this.createdAt = now;
        this.activatedAt = now;
    }

    public void addStop(TripRouteRevisionStopEntity stop) {
        stops.add(stop);
        stop.assignTo(this);
    }

    public void addSection(TripRouteRevisionSectionEntity section) {
        sections.add(section);
        section.assignTo(this);
    }

    public void supersede(Instant now) {
        this.status = RouteRevisionStatus.SUPERSEDED;
        this.supersededAt = now;
    }
}
