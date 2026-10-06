package com.quangkhai.vehicletracking_backend.simulation.entity;

import java.time.Instant;
import java.util.UUID;

import com.quangkhai.vehicletracking_backend.reroute.entity.NotificationSeverity;
import com.quangkhai.vehicletracking_backend.driver.entity.DriverEntity;
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
@Table(name = "simulation_incidents", schema = "vehicle_tracking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SimulationIncidentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private TripEntity trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_by_driver_id")
    private DriverEntity reportedByDriver;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SimulationIncidentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SimulationIncidentStatus status = SimulationIncidentStatus.OPEN;

    @Column(length = 500)
    private String detail;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "simulated_elapsed_seconds", nullable = false)
    private double simulatedElapsedSeconds;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private UUID idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public SimulationIncidentEntity(TripEntity trip, DriverEntity reportedByDriver, int attemptNumber, SimulationIncidentType type,
                                    NotificationSeverity severity, String detail, double latitude,
                                    double longitude, double simulatedElapsedSeconds, UUID idempotencyKey,
                                    Instant createdAt) {
        this.trip = trip;
        this.reportedByDriver = reportedByDriver;
        this.attemptNumber = attemptNumber;
        this.type = type;
        this.severity = severity;
        this.detail = detail == null || detail.isBlank() ? null : detail.trim();
        this.latitude = latitude;
        this.longitude = longitude;
        this.simulatedElapsedSeconds = simulatedElapsedSeconds;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
    }

    public void acknowledge(Instant now) {
        if (status == SimulationIncidentStatus.OPEN) {
            status = SimulationIncidentStatus.ACKNOWLEDGED;
            acknowledgedAt = now;
        }
    }

    public void resolve(Instant now) {
        if (status != SimulationIncidentStatus.RESOLVED) {
            status = SimulationIncidentStatus.RESOLVED;
            resolvedAt = now;
        }
    }
}
