package com.quangkhai.vehicletracking_backend.route.dto;

import com.quangkhai.vehicletracking_backend.route.entity.RouteEntity;
import com.quangkhai.vehicletracking_backend.route.entity.RouteTransportMode;
import com.quangkhai.vehicletracking_backend.route.entity.RoutingProviderName;

import java.time.Instant;

public record RouteSummaryResponse(
        Long id,
        String name,
        RouteTransportMode transportMode,
        RoutingProviderName routingProvider,
        String startStationName,
        String endStationName,
        int stopCount,
        long totalDistanceMeters,
        long estimatedTravelDurationSeconds,
        long totalDwellDurationSeconds,
        long estimatedTripDurationSeconds,
        Instant calculatedAt,
        Instant createdAt
) {
    public static RouteSummaryResponse from(RouteEntity entity) {
        String startName = entity.getStops().isEmpty() ? "" : entity.getStops().get(0).getStationNameSnapshot();
        String endName = entity.getStops().isEmpty() ? "" : entity.getStops().get(entity.getStops().size() - 1).getStationNameSnapshot();

        return new RouteSummaryResponse(
                entity.getId(),
                entity.getName(),
                entity.getTransportMode(),
                entity.getRoutingProvider(),
                startName,
                endName,
                entity.getStops().size(),
                entity.getTotalDistanceMeters(),
                entity.getEstimatedTravelDurationSeconds(),
                entity.getTotalDwellDurationSeconds(),
                entity.getEstimatedTripDurationSeconds(),
                entity.getCalculatedAt(),
                entity.getCreatedAt()
        );
    }
}
