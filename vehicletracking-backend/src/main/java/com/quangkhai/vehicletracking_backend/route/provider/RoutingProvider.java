package com.quangkhai.vehicletracking_backend.route.provider;

import java.util.List;

public interface RoutingProvider {

    CalculatedRoute calculate(List<RoutingWaypoint> waypoints);
}
