package com.quangkhai.vehicletracking_backend.dashboard.dto;

import com.quangkhai.vehicletracking_backend.reroute.dto.NotificationResponse;

import java.time.Instant;
import java.util.List;

public record DashboardSummaryResponse(
        Instant serverTime,
        long activeVehicleCount,
        long activeDriverCount,
        long tripsInProgress,
        long scheduledTrips,
        long completedTrips,
        long cancelledTrips,
        long overdueTrips,
        long offRouteVehicleCount,
        long unreadAlertCount,
        List<NotificationResponse> pendingAlerts) {
}
