package com.quangkhai.vehicletracking_backend.reroute.entity;

import com.quangkhai.vehicletracking_backend.route.entity.PolylineEncoding;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.quangkhai.vehicletracking_backend.route.entity.RouteTrafficInterval;
import com.quangkhai.vehicletracking_backend.route.entity.TrafficSpeedCategory;
import java.util.ArrayList;
import java.util.List;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "polyline_encoding", nullable = false, length = 40)
    private PolylineEncoding polylineEncoding = PolylineEncoding.HERE_FLEXIBLE_POLYLINE;

    @Column(name = "traffic_intervals", columnDefinition = "TEXT")
    private String trafficIntervalsValue;

    @Column(name = "distance_meters", nullable = false)
    private long distanceMeters;

    @Column(name = "travel_duration_seconds", nullable = false)
    private long travelDurationSeconds;

    @Column(name = "base_travel_duration_seconds", nullable = false)
    private long baseTravelDurationSeconds;

    public TripRouteRevisionSectionEntity(int sectionSequence, int destinationStopSequence,
                                          String encodedPolyline, long distanceMeters,
                                          long travelDurationSeconds, long baseTravelDurationSeconds) {
        this(sectionSequence, destinationStopSequence, encodedPolyline, PolylineEncoding.HERE_FLEXIBLE_POLYLINE,
                distanceMeters, travelDurationSeconds, baseTravelDurationSeconds, List.of());
    }

    public TripRouteRevisionSectionEntity(int sectionSequence, int destinationStopSequence,
                                          String encodedPolyline, PolylineEncoding polylineEncoding,
                                          long distanceMeters, long travelDurationSeconds,
                                          long baseTravelDurationSeconds) {
        this(sectionSequence, destinationStopSequence, encodedPolyline, polylineEncoding,
                distanceMeters, travelDurationSeconds, baseTravelDurationSeconds, List.of());
    }

    public TripRouteRevisionSectionEntity(int sectionSequence, int destinationStopSequence,
                                          String encodedPolyline, PolylineEncoding polylineEncoding,
                                          long distanceMeters, long travelDurationSeconds,
                                          long baseTravelDurationSeconds, List<RouteTrafficInterval> trafficIntervals) {
        this.sectionSequence = sectionSequence;
        this.destinationStopSequence = destinationStopSequence;
        this.encodedPolyline = encodedPolyline;
        this.polylineEncoding = polylineEncoding;
        this.distanceMeters = distanceMeters;
        this.travelDurationSeconds = travelDurationSeconds;
        this.baseTravelDurationSeconds = baseTravelDurationSeconds;
        this.trafficIntervalsValue = trafficIntervals == null || trafficIntervals.isEmpty() ? null
                : trafficIntervals.stream().map(item -> item.startPolylinePointIndex() + ":"
                + item.endPolylinePointIndex() + ":" + item.category().name())
                .collect(java.util.stream.Collectors.joining(";"));
    }

    public List<RouteTrafficInterval> getTrafficIntervals() {
        if (trafficIntervalsValue == null || trafficIntervalsValue.isBlank()) return List.of();
        List<RouteTrafficInterval> result = new ArrayList<>();
        for (String token : trafficIntervalsValue.split(";")) {
            String[] values = token.split(":", -1);
            if (values.length != 3) continue;
            try { result.add(new RouteTrafficInterval(Integer.parseInt(values[0]), Integer.parseInt(values[1]),
                    TrafficSpeedCategory.valueOf(values[2]))); }
            catch (IllegalArgumentException ignored) { }
        }
        return List.copyOf(result);
    }

    void assignTo(TripRouteRevisionEntity revision) {
        this.revision = revision;
    }
}
