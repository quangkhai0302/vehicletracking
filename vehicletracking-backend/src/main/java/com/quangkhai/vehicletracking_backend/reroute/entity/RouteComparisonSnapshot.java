package com.quangkhai.vehicletracking_backend.reroute.entity;

import java.util.List;

/** Immutable geometry and event-time estimates; never recomputed from the live route. */
public record RouteComparisonSnapshot(int attemptNumber, Long previousRevisionId, Anchor anchor, Path before, Path after) {
    public RouteComparisonSnapshot {
        if (attemptNumber < 1 || anchor == null || before == null || after == null
                || (previousRevisionId != null && previousRevisionId < 1)) throw new IllegalArgumentException("Invalid route comparison");
    }
    public record Anchor(double latitude, double longitude) {
        public Anchor {
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180)
                throw new IllegalArgumentException("Invalid comparison anchor");
        }
    }
    public record Path(List<String> encodedPolylines, long distanceMeters, Long durationSeconds) {
        public Path {
            encodedPolylines = List.copyOf(encodedPolylines);
            if (encodedPolylines.isEmpty() || encodedPolylines.stream().anyMatch(String::isBlank)
                    || distanceMeters < 0 || (durationSeconds != null && durationSeconds < 0))
                throw new IllegalArgumentException("Invalid comparison path");
        }
    }
}
