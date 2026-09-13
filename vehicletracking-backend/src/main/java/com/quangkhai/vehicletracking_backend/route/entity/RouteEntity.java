package com.quangkhai.vehicletracking_backend.route.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "routes", schema = "vehicle_tracking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RouteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_mode", nullable = false, length = 20)
    private RouteTransportMode transportMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "routing_provider", nullable = false, length = 20)
    private RoutingProviderName routingProvider;

    @Column(name = "total_distance_meters", nullable = false)
    private Long totalDistanceMeters;

    @Column(name = "estimated_travel_duration_seconds", nullable = false)
    private Long estimatedTravelDurationSeconds;

    @Column(name = "base_travel_duration_seconds", nullable = false)
    private Long baseTravelDurationSeconds;

    @Column(name = "total_dwell_duration_seconds", nullable = false)
    private Long totalDwellDurationSeconds;

    @Column(name = "estimated_trip_duration_seconds", nullable = false)
    private Long estimatedTripDurationSeconds;

    @Column(name = "estimated_departure_at", nullable = false)
    private Instant estimatedDepartureAt;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNumber ASC")
    @BatchSize(size = 50)
    private List<RouteStopEntity> stops = new ArrayList<>();

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sectionSequence ASC")
    @BatchSize(size = 50)
    private List<RouteSectionEntity> sections = new ArrayList<>();

    public RouteEntity(
            String name,
            RouteTransportMode transportMode,
            RoutingProviderName routingProvider,
            Long totalDistanceMeters,
            Long estimatedTravelDurationSeconds,
            Long baseTravelDurationSeconds,
            Long totalDwellDurationSeconds,
            Long estimatedTripDurationSeconds,
            Instant estimatedDepartureAt,
            Instant calculatedAt
    ) {
        this.name = name;
        this.transportMode = transportMode;
        this.routingProvider = routingProvider;
        this.totalDistanceMeters = totalDistanceMeters;
        this.estimatedTravelDurationSeconds = estimatedTravelDurationSeconds;
        this.baseTravelDurationSeconds = baseTravelDurationSeconds;
        this.totalDwellDurationSeconds = totalDwellDurationSeconds;
        this.estimatedTripDurationSeconds = estimatedTripDurationSeconds;
        this.estimatedDepartureAt = estimatedDepartureAt;
        this.calculatedAt = calculatedAt;
    }

    @PrePersist
    void initCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void addStop(RouteStopEntity stop) {
        stops.add(stop);
        stop.setRoute(this);
    }

    public void addSection(RouteSectionEntity section) {
        sections.add(section);
        section.setRoute(this);
    }
}
