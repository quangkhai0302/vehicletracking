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

    @Column(name = "geometry_version", nullable = false)
    private Long geometryVersion = 1L;

    @Column(name = "provider_content_expires_at")
    private Instant providerContentExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNumber ASC")
    @BatchSize(size = 50)
    private List<RouteStopEntity> stops = new ArrayList<>();

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sectionSequence ASC")
    @BatchSize(size = 50)
    private List<RouteSectionEntity> sections = new ArrayList<>();

    @OneToMany(mappedBy="route",cascade=CascadeType.ALL,orphanRemoval=true)
    @OrderBy("pointOrder ASC")
    private List<RouteShapePointEntity> shapingPoints = new ArrayList<>();

    public void addShapingPoint(RouteShapePointEntity point) { shapingPoints.add(point); point.setRoute(this); }

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
        this.providerContentExpiresAt = routingProvider == RoutingProviderName.GOOGLE
                ? calculatedAt.plusSeconds(30L * 24 * 60 * 60) : null;
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

    public void deactivate() { active = false; }

    public void replaceDefinition(RouteEntity replacement) {
        replaceDefinitionMetadata(replacement);
        clearDefinitionChildren();
        appendDefinitionChildren(replacement);
    }

    /**
     * Copies the scalar route snapshot fields without touching the child
     * collections.  Updates use this as the first phase of a two-phase
     * replacement so orphan removal can be flushed before new rows reuse the
     * same route/sequence keys.
     */
    public void replaceDefinitionMetadata(RouteEntity replacement) {
        this.name = replacement.name;
        this.transportMode = replacement.transportMode;
        this.routingProvider = replacement.routingProvider;
        this.totalDistanceMeters = replacement.totalDistanceMeters;
        this.estimatedTravelDurationSeconds = replacement.estimatedTravelDurationSeconds;
        this.baseTravelDurationSeconds = replacement.baseTravelDurationSeconds;
        this.totalDwellDurationSeconds = replacement.totalDwellDurationSeconds;
        this.estimatedTripDurationSeconds = replacement.estimatedTripDurationSeconds;
        this.estimatedDepartureAt = replacement.estimatedDepartureAt;
        this.calculatedAt = replacement.calculatedAt;
        this.geometryVersion = Math.max(1L, this.geometryVersion == null ? 1L : this.geometryVersion + 1L);
        this.providerContentExpiresAt = replacement.providerContentExpiresAt;
    }

    /**
     * Removes the old stop and section snapshots.  The owning service must
     * flush after this call before appending replacement children.
     */
    public void clearDefinitionChildren() {
        this.stops.clear();
        this.sections.clear();
        this.shapingPoints.clear();
    }

    /**
     * Attaches replacement stop and section snapshots to this route.
     */
    public void appendDefinitionChildren(RouteEntity replacement) {
        replacement.stops.forEach(this::addStop);
        replacement.sections.forEach(this::addSection);
        replacement.shapingPoints.forEach(this::addShapingPoint);
    }
}
