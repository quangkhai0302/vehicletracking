package com.quangkhai.vehicletracking_backend.traffic;

import java.time.Instant;
import java.util.List;

public record TrafficIncident(
        String id,
        String description,
        String type,
        String criticality,
        Instant startTime,
        Instant endTime,
        List<List<Double>> points,
        List<Double> center,
        String status
) {
    public TrafficIncident {
        points = points == null ? List.of() : List.copyOf(points);
        center = center == null ? List.of() : List.copyOf(center);
    }
}
