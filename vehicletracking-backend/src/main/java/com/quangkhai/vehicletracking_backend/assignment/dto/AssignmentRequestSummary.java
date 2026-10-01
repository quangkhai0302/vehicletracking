package com.quangkhai.vehicletracking_backend.assignment.dto;

import com.quangkhai.vehicletracking_backend.assignment.entity.TripAssignmentRequestEntity;
import com.quangkhai.vehicletracking_backend.assignment.entity.TripAssignmentStatus;
import java.time.Instant;
import java.util.UUID;

public record AssignmentRequestSummary(UUID id, TripAssignmentStatus status, long candidateDriverId,
                                       String candidateDriverName, Instant requestedAt, Instant respondedAt,
                                       String responseReason) {
    public static AssignmentRequestSummary from(TripAssignmentRequestEntity request) {
        return new AssignmentRequestSummary(request.getId(), request.getStatus(),
                request.getCandidateDriver().getId(), request.getCandidateDriver().getFullName(),
                request.getRequestedAt(), request.getRespondedAt(), request.getResponseReason());
    }
}
