package com.quangkhai.vehicletracking_backend.reroute.entity;

import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "trip_off_route_alert_states", schema = "vehicle_tracking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripOffRouteAlertStateEntity {
    @Id
    @Column(name = "trip_id")
    private Long tripId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "trip_id", nullable = false)
    private TripEntity trip;

    @Column(name = "attempt_number", nullable = false) private int attemptNumber;
    @Column(name = "breach_started_at") private Instant breachStartedAt;
    @Column(name = "consecutive_breach_count", nullable = false) private int consecutiveBreachCount;
    @Column(nullable = false) private boolean active;
    @Column(nullable = false) private int episode;
    @Column(name = "last_distance_meters") private Double lastDistanceMeters;
    @Column(name = "last_recorded_at") private Instant lastRecordedAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public TripOffRouteAlertStateEntity(TripEntity trip, int attemptNumber, Instant now) {
        this.trip = trip;
        this.attemptNumber = attemptNumber;
        this.updatedAt = now;
    }

    public void resetForAttempt(int attemptNumber, Instant now) {
        this.attemptNumber = attemptNumber;
        this.breachStartedAt = null;
        this.consecutiveBreachCount = 0;
        this.active = false;
        this.episode = 0;
        this.lastDistanceMeters = null;
        this.lastRecordedAt = null;
        this.updatedAt = now;
    }

    public void observeBreach(double distanceMeters, Instant recordedAt, Instant now) {
        if (breachStartedAt == null) breachStartedAt = recordedAt;
        consecutiveBreachCount++;
        lastDistanceMeters = distanceMeters;
        lastRecordedAt = recordedAt;
        updatedAt = now;
    }

    public void markActive(Instant now) {
        active = true;
        episode++;
        updatedAt = now;
    }

    public void clear(Instant recordedAt, Instant now) {
        breachStartedAt = null;
        consecutiveBreachCount = 0;
        active = false;
        lastDistanceMeters = null;
        lastRecordedAt = recordedAt;
        updatedAt = now;
    }
}
