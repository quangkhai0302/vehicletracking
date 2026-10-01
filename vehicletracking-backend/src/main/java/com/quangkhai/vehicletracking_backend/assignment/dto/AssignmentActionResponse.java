package com.quangkhai.vehicletracking_backend.assignment.dto;

import com.quangkhai.vehicletracking_backend.assignment.entity.TripAssignmentRequestEntity;
import com.quangkhai.vehicletracking_backend.assignment.entity.TripAssignmentStatus;
import java.util.UUID;

public record AssignmentActionResponse(UUID requestId, TripAssignmentStatus status, long tripId) {
    public static AssignmentActionResponse from(TripAssignmentRequestEntity request) {
        return new AssignmentActionResponse(request.getId(), request.getStatus(), request.getTrip().getId());
    }
}
