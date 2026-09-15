package com.quangkhai.vehicletracking_backend.route.entity;

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

import java.util.ArrayList;
import java.util.List;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "polyline_encoding", nullable = false, length = 40)
    private PolylineEncoding polylineEncoding = PolylineEncoding.HERE_FLEXIBLE_POLYLINE;

    @Column(name = "traffic_intervals", columnDefinition = "TEXT")
    private String trafficIntervalsValue;

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
        this(sectionSequence, destinationStopSequence, encodedPolyline, PolylineEncoding.HERE_FLEXIBLE_POLYLINE,
                distanceMeters, travelDurationSeconds, baseTravelDurationSeconds, List.of());
    }

    public RouteSectionEntity(
            Integer sectionSequence,
            Integer destinationStopSequence,
            String encodedPolyline,
            PolylineEncoding polylineEncoding,
            Long distanceMeters,
            Long travelDurationSeconds,
            Long baseTravelDurationSeconds
    ) {
        this(sectionSequence, destinationStopSequence, encodedPolyline, polylineEncoding, distanceMeters,
                travelDurationSeconds, baseTravelDurationSeconds, List.of());
    }

    public RouteSectionEntity(
            Integer sectionSequence,
            Integer destinationStopSequence,
            String encodedPolyline,
            PolylineEncoding polylineEncoding,
            Long distanceMeters,
            Long travelDurationSeconds,
            Long baseTravelDurationSeconds,
            List<RouteTrafficInterval> trafficIntervals
    ) {
        this.sectionSequence = sectionSequence;
        this.destinationStopSequence = destinationStopSequence;
        this.encodedPolyline = encodedPolyline;
        this.polylineEncoding = polylineEncoding;
        this.distanceMeters = distanceMeters;
        this.travelDurationSeconds = travelDurationSeconds;
        this.baseTravelDurationSeconds = baseTravelDurationSeconds;
        this.trafficIntervalsValue = serializeTrafficIntervals(trafficIntervals);
    }

    public List<RouteTrafficInterval> getTrafficIntervals() {
        if (trafficIntervalsValue == null || trafficIntervalsValue.isBlank()) return List.of();
        List<RouteTrafficInterval> result = new ArrayList<>();
        for (String token : trafficIntervalsValue.split(";")) {
            String[] values = token.split(":", -1);
            if (values.length != 3) continue;
            try {
                result.add(new RouteTrafficInterval(Integer.parseInt(values[0]), Integer.parseInt(values[1]),
                        TrafficSpeedCategory.valueOf(values[2])));
            } catch (IllegalArgumentException ignored) {
                // Corrupt optional traffic metadata must not make valid route geometry unreadable.
            }
        }
        return List.copyOf(result);
    }

    private String serializeTrafficIntervals(List<RouteTrafficInterval> intervals) {
        if (intervals == null || intervals.isEmpty()) return null;
        return intervals.stream().map(item -> item.startPolylinePointIndex() + ":"
                + item.endPolylinePointIndex() + ":" + item.category().name()).collect(java.util.stream.Collectors.joining(";"));
    }

    void setRoute(RouteEntity route) {
        this.route = route;
    }
}
