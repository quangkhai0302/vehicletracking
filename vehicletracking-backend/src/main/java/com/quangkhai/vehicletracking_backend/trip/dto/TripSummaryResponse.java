package com.quangkhai.vehicletracking_backend.trip.dto;

import com.quangkhai.vehicletracking_backend.trip.entity.*;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleType;
import java.time.Instant;
import java.util.Comparator;

public record TripSummaryResponse(Long id, Long vehicleId, String vehiclePlateNumber, Long routeId,
        String routeName, TripStatus status, Instant scheduledDepartureAt, Instant plannedEndAt,
        Instant startedAt, Instant endedAt, Instant createdAt, int attemptNumber, VehicleType vehicleType) {
    public TripSummaryResponse(Long id, Long vehicleId, String plate, Long routeId, String routeName, TripStatus status,
            Instant scheduled, Instant planned, Instant started, Instant ended, Instant created) {
        this(id,vehicleId,plate,routeId,routeName,status,scheduled,planned,started,ended,created,1,VehicleType.CAR);
    }
    public TripSummaryResponse(Long id, Long vehicleId, String plate, Long routeId, String routeName, TripStatus status,
            Instant scheduled, Instant planned, Instant started, Instant ended, Instant created, int attemptNumber) {
        this(id,vehicleId,plate,routeId,routeName,status,scheduled,planned,started,ended,created,attemptNumber,VehicleType.CAR);
    }
    public static TripSummaryResponse from(TripEntity trip) {
        return new TripSummaryResponse(trip.getId(), trip.getVehicle().getId(), trip.getVehiclePlateSnapshot(),
                trip.getRoute().getId(), trip.getRoute().getName(), trip.getStatus(), trip.getScheduledDepartureAt(),
                plannedEndAt(trip),
                trip.getStartedAt(), trip.getEndedAt(), trip.getCreatedAt(), trip.getAttemptNumber(),
                trip.getVehicle().getVehicleType());
    }

    private static Instant plannedEndAt(TripEntity trip) {
        return trip.getStops().stream()
                .max(Comparator.comparing(TripStopEntity::getSequenceNumber))
                .map(TripStopEntity::getPlannedArrivalAt)
                .orElseGet(() -> trip.getScheduledDepartureAt()
                        .plusSeconds(trip.getRoute().getEstimatedTripDurationSeconds()));
    }
}
