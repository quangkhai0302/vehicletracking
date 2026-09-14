package com.quangkhai.vehicletracking_backend.traffic;

import java.time.Instant;
import java.util.List;

public record TrafficEnvelope<T>(
        TrafficSource source,
        TrafficStatus status,
        Instant observedAt,
        Instant fetchedAt,
        long ageSeconds,
        String warning,
        List<T> results
) {
    public TrafficEnvelope {
        results = results == null ? List.of() : List.copyOf(results);
    }
}
