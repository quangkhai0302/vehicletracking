package com.quangkhai.vehicletracking_backend.route.provider;

import com.quangkhai.vehicletracking_backend.route.entity.PolylineEncoding;
import com.quangkhai.vehicletracking_backend.route.entity.RouteTrafficInterval;

import java.util.List;

public record CalculatedSection(
        int sectionSequence,
        int destinationStopSequence,
        String encodedPolyline,
        PolylineEncoding polylineEncoding,
        long distanceMeters,
        long travelDurationSeconds,
        long baseTravelDurationSeconds,
        List<RouteTrafficInterval> trafficIntervals
) {
    public CalculatedSection {
        trafficIntervals = trafficIntervals == null ? List.of() : List.copyOf(trafficIntervals);
    }
    public CalculatedSection(int sectionSequence, int destinationStopSequence, String encodedPolyline,
                             long distanceMeters, long travelDurationSeconds, long baseTravelDurationSeconds) {
        this(sectionSequence, destinationStopSequence, encodedPolyline, PolylineEncoding.HERE_FLEXIBLE_POLYLINE,
                distanceMeters, travelDurationSeconds, baseTravelDurationSeconds, List.of());
    }
    public CalculatedSection(int sectionSequence, int destinationStopSequence, String encodedPolyline,
                             PolylineEncoding polylineEncoding, long distanceMeters,
                             long travelDurationSeconds, long baseTravelDurationSeconds) {
        this(sectionSequence, destinationStopSequence, encodedPolyline, polylineEncoding,
                distanceMeters, travelDurationSeconds, baseTravelDurationSeconds, List.of());
    }
}
