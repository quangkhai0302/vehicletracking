package com.quangkhai.vehicletracking_backend.driverportal.dto;

import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse.RouteSectionResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DriverRouteOptionsResponse(UUID token, Instant expiresAt, Long routeRevisionId, List<Option> options) {
    public record Option(int optionIndex, String label, long distanceMeters, long durationSeconds,
                         List<RouteSectionResponse> sections) {}
}
