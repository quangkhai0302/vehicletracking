package com.quangkhai.vehicletracking_backend.reroute.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "trip_traffic_alert_states", schema = "vehicle_tracking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripTrafficAlertStateEntity {
    @Id
    @Column(name = "trip_id")
    private Long tripId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "trip_id", nullable = false)
    private com.quangkhai.vehicletracking_backend.trip.entity.TripEntity trip;

    @Column(name = "last_traffic_fetched_at")
    private Instant lastTrafficFetchedAt;

    @Column(name = "breach_fingerprint", length = 255)
    private String breachFingerprint;

    @Column(name = "breach_count", nullable = false)
    private int breachCount;

    @Column(name = "last_triggered_fingerprint", length = 255)
    private String lastTriggeredFingerprint;

    @Column(name = "last_trigger_at")
    private Instant lastTriggerAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public TripTrafficAlertStateEntity(com.quangkhai.vehicletracking_backend.trip.entity.TripEntity trip, Instant now) {
        this.trip = trip;
        // Leave the derived ID unset until persist. A preassigned ID makes
        // Spring Data choose merge for this new @MapsId entity, which fails
        // while Hibernate copies the shared-primary-key association.
        this.updatedAt = now;
    }

    public void observe(Instant fetchedAt, String fingerprint, int count, Instant now) {
        this.lastTrafficFetchedAt = fetchedAt;
        this.breachFingerprint = fingerprint;
        this.breachCount = Math.max(0, count);
        this.updatedAt = now;
    }

    public void clear(Instant fetchedAt, Instant now) {
        // Keep the provider timestamp as a checkpoint even when the breach
        // clears. A cached response with the same fetchedAt must not be
        // treated as a new traffic observation on the next evaluation.
        if (fetchedAt != null) this.lastTrafficFetchedAt = fetchedAt;
        this.breachFingerprint = null;
        this.breachCount = 0;
        this.updatedAt = now;
    }

    public void clear(Instant now) {
        clear(null, now);
    }

    public void markTriggered(String fingerprint, Instant now) {
        this.lastTriggeredFingerprint = fingerprint;
        this.lastTriggerAt = now;
        this.updatedAt = now;
    }
    public void replay(Instant now) {
        lastTrafficFetchedAt=null; breachFingerprint=null; breachCount=0;
        lastTriggeredFingerprint=null; lastTriggerAt=null; updatedAt=now;
    }
}
