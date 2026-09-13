package com.quangkhai.vehicletracking_backend.trip.dto;

import com.quangkhai.vehicletracking_backend.trip.entity.*;
import java.time.Instant;

public record TripSummaryResponse(Long id, Long vehicleId, String vehiclePlateNumber, Long routeId,
        String routeName, TripStatus status, Instant scheduledDepartureAt, Instant plannedEndAt,
        Instant startedAt, Instant endedAt, Instant createdAt) {
    public static TripSummaryResponse from(TripEntity trip) {
        return new TripSummaryResponse(trip.getId(), trip.getVehicle().getId(), trip.getVehiclePlateSnapshot(),
                trip.getRoute().getId(), trip.getRoute().getName(), trip.getStatus(), trip.getScheduledDepartureAt(),
                trip.getScheduledDepartureAt().plusSeconds(trip.getRoute().getEstimatedTripDurationSeconds()),
                trip.getStartedAt(), trip.getEndedAt(), trip.getCreatedAt());
    }
}
