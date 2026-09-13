package com.quangkhai.vehicletracking_backend.route.provider;

import java.math.BigDecimal;

public record RoutingWaypoint(
        Long stationId,
        String stationName,
        BigDecimal latitude,
        BigDecimal longitude,
        int sequenceNumber,
        int dwellDurationSeconds
) {
}
