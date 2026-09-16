package com.quangkhai.vehicletracking_backend.route.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "route_sections", schema = "vehicle_tracking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RouteSectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private RouteEntity route;

    @Column(name = "section_sequence", nullable = false)
    private Integer sectionSequence;

    @Column(name = "destination_stop_sequence", nullable = false)
    private Integer destinationStopSequence;

    @Column(name = "encoded_polyline", nullable = false, columnDefinition = "TEXT")
    private String encodedPolyline;

    @Column(name = "distance_meters", nullable = false)
    private Long distanceMeters;

    @Column(name = "travel_duration_seconds", nullable = false)
    private Long travelDurationSeconds;

    @Column(name = "base_travel_duration_seconds", nullable = false)
    private Long baseTravelDurationSeconds;

    public RouteSectionEntity(
            Integer sectionSequence,
            Integer destinationStopSequence,
            String encodedPolyline,
            Long distanceMeters,
            Long travelDurationSeconds,
            Long baseTravelDurationSeconds
    ) {
        this.sectionSequence = sectionSequence;
        this.destinationStopSequence = destinationStopSequence;
        this.encodedPolyline = encodedPolyline;
        this.distanceMeters = distanceMeters;
        this.travelDurationSeconds = travelDurationSeconds;
        this.baseTravelDurationSeconds = baseTravelDurationSeconds;
    }

    void setRoute(RouteEntity route) {
        this.route = route;
    }
}
