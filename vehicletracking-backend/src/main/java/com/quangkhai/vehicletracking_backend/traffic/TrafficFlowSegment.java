package com.quangkhai.vehicletracking_backend.traffic;

import java.util.List;

public record TrafficFlowSegment(
        String id,
        String description,
        double lengthMeters,
        List<List<Double>> points,
        double speedKmh,
        double freeFlowKmh,
        double jamFactor,
        String traversability,
        Double confidence
) {
    public TrafficFlowSegment {
        points = points == null ? List.of() : List.copyOf(points);
    }
}
