package com.quangkhai.vehicletracking_backend.route.entity;

public record RouteTrafficInterval(int startPolylinePointIndex, int endPolylinePointIndex,
                                   TrafficSpeedCategory category) {
    public RouteTrafficInterval {
        if (startPolylinePointIndex < 0 || endPolylinePointIndex <= startPolylinePointIndex) {
            throw new IllegalArgumentException("Invalid traffic interval indexes");
        }
        category = category == null ? TrafficSpeedCategory.UNKNOWN : category;
    }
}
