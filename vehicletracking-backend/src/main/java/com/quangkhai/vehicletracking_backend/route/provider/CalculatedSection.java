package com.quangkhai.vehicletracking_backend.route.provider;

import com.quangkhai.vehicletracking_backend.route.dto.RouteInstruction;
import java.util.List;

public record CalculatedSection(
        int sectionSequence,
        int destinationStopSequence,
        String encodedPolyline,
        long distanceMeters,
        long travelDurationSeconds,
        long baseTravelDurationSeconds,
        List<RouteInstruction> instructions
) {
    public CalculatedSection(int sequence, int destination, String polyline, long distance, long travel, long base) {
        this(sequence, destination, polyline, distance, travel, base, List.of());
    }
}
