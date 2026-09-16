package com.quangkhai.vehicletracking_backend.reroute.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "trip_route_revision_sections", schema = "vehicle_tracking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripRouteRevisionSectionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "revision_id", nullable = false)
    private TripRouteRevisionEntity revision;

    @Column(name = "section_sequence", nullable = false)
    private int sectionSequence;

    @Column(name = "destination_stop_sequence", nullable = false)
    private int destinationStopSequence;

    @Column(name = "encoded_polyline", nullable = false, columnDefinition = "TEXT")
    private String encodedPolyline;

    @Column(name = "distance_meters", nullable = false)
    private long distanceMeters;

    @Column(name = "travel_duration_seconds", nullable = false)
    private long travelDurationSeconds;

    @Column(name = "base_travel_duration_seconds", nullable = false)
    private long baseTravelDurationSeconds;

    public TripRouteRevisionSectionEntity(int sectionSequence, int destinationStopSequence,
                                          String encodedPolyline, long distanceMeters,
                                          long travelDurationSeconds, long baseTravelDurationSeconds) {
        this.sectionSequence = sectionSequence;
        this.destinationStopSequence = destinationStopSequence;
        this.encodedPolyline = encodedPolyline;
        this.distanceMeters = distanceMeters;
        this.travelDurationSeconds = travelDurationSeconds;
        this.baseTravelDurationSeconds = baseTravelDurationSeconds;
    }

    void assignTo(TripRouteRevisionEntity revision) {
        this.revision = revision;
    }
}
