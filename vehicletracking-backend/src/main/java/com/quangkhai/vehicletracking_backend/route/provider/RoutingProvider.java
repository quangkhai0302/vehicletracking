package com.quangkhai.vehicletracking_backend.route.provider;

import java.util.List;

public interface RoutingProvider {

    CalculatedRoute calculate(List<RoutingWaypoint> waypoints);

    default List<CalculatedRoute> calculateAlternatives(List<RoutingWaypoint> waypoints) {
        return List.of(calculate(waypoints));
    }
}
