package com.quangkhai.vehicletracking_backend.reroute.dto;

import com.quangkhai.vehicletracking_backend.reroute.entity.RouteComparisonSnapshot.Anchor;
import com.quangkhai.vehicletracking_backend.reroute.entity.RouteComparisonSnapshot.Path;
import java.time.Instant;

public record RouteComparisonResponse(long tripId, long revisionId, int revisionNumber, Instant createdAt,
        String reason, String status, String message, Integer attemptNumber, Anchor anchor, Path before, Path after) {}
