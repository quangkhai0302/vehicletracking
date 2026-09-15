package com.quangkhai.vehicletracking_backend.route.provider;

import com.quangkhai.vehicletracking_backend.route.entity.RouteTransportMode;

import java.time.Instant;
import java.util.List;

public record RoutingRequest(
        List<RoutingWaypoint> waypoints,
        RouteTransportMode transportMode,
        Instant departureTime,
        boolean alternatives
) {
    public RoutingRequest {
        waypoints = waypoints == null ? List.of() : List.copyOf(waypoints);
        transportMode = transportMode == null ? RouteTransportMode.CAR : transportMode;
        departureTime = departureTime == null ? Instant.now() : departureTime;
    }

    public static RoutingRequest standard(List<RoutingWaypoint> waypoints, RouteTransportMode transportMode) {
        return new RoutingRequest(waypoints, transportMode, Instant.now(), false);
    }
}
