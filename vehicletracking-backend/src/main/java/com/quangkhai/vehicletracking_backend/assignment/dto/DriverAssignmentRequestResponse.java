package com.quangkhai.vehicletracking_backend.assignment.dto;

import com.quangkhai.vehicletracking_backend.assignment.entity.TripAssignmentRequestEntity;
import java.time.Instant;
import java.util.UUID;

public record DriverAssignmentRequestResponse(UUID requestId, long tripId, String routeName,
                                              String vehiclePlate, Instant tripCreatedAt,
                                              Instant requestedAt) {
    public static DriverAssignmentRequestResponse from(TripAssignmentRequestEntity request) {
        var trip = request.getTrip();
        return new DriverAssignmentRequestResponse(request.getId(), trip.getId(), trip.getRoute().getName(),
                trip.getVehiclePlateSnapshot(), trip.getCreatedAt(), request.getRequestedAt());
    }
}
