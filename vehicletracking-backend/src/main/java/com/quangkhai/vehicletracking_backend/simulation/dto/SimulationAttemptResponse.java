package com.quangkhai.vehicletracking_backend.simulation.dto;
import com.quangkhai.vehicletracking_backend.simulation.entity.*;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import java.time.Instant;
public record SimulationAttemptResponse(long tripId, int attemptNumber, SimulationStatus status, TripStatus tripStatus,
        double elapsedSeconds, int multiplier, Instant scheduledDepartureAt, Instant startedAt, Instant endedAt,
        Instant archivedAt, String errorMessage) {
    public static SimulationAttemptResponse from(SimulationAttemptEntity a) {
        return new SimulationAttemptResponse(a.getTripId(),a.getAttemptNumber(),a.getStatus(),a.getTripStatus(),
            a.getElapsedSeconds(),a.getMultiplier(),a.getScheduledDepartureAt(),a.getStartedAt(),a.getEndedAt(),a.getArchivedAt(),a.getErrorMessage());
    }
}
