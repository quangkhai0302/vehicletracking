package com.quangkhai.vehicletracking_backend.route.provider;

public record CalculatedSection(
        int sectionSequence,
        int destinationStopSequence,
        String encodedPolyline,
        long distanceMeters,
        long travelDurationSeconds,
        long baseTravelDurationSeconds
) {
}
