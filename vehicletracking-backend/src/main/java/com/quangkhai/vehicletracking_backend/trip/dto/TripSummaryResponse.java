package com.quangkhai.vehicletracking_backend.trip.dto;

import com.quangkhai.vehicletracking_backend.driver.dto.DriverSnapshotResponse;
import com.quangkhai.vehicletracking_backend.assignment.dto.AssignmentRequestSummary;
import com.quangkhai.vehicletracking_backend.trip.entity.*;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleType;
import java.time.Instant;
import java.util.Comparator;

public record TripSummaryResponse(Long id, Long vehicleId, String vehiclePlateNumber, Long routeId,
        String routeName, TripStatus status, Instant scheduledDepartureAt, Instant plannedEndAt,
        Instant startedAt, Instant endedAt, Instant createdAt, int attemptNumber, VehicleType vehicleType,
        DriverSnapshotResponse driver, String cancellationReason, TripDispatchMode dispatchMode,
        Long scheduleId, String scheduleName, AssignmentRequestSummary assignmentRequest) {
    public TripSummaryResponse(Long id, Long vehicleId, String plate, Long routeId, String routeName, TripStatus status,
            Instant scheduled, Instant planned, Instant started, Instant ended, Instant created) {
        this(id,vehicleId,plate,routeId,routeName,status,scheduled,planned,started,ended,created,1,VehicleType.CAR,null,null,
                TripDispatchMode.ON_DEMAND,null,null,null);
    }
    public TripSummaryResponse(Long id, Long vehicleId, String plate, Long routeId, String routeName, TripStatus status,
            Instant scheduled, Instant planned, Instant started, Instant ended, Instant created, int attemptNumber) {
        this(id,vehicleId,plate,routeId,routeName,status,scheduled,planned,started,ended,created,attemptNumber,VehicleType.CAR,null,null,
                TripDispatchMode.ON_DEMAND,null,null,null);
    }
    public TripSummaryResponse(Long id, Long vehicleId, String plate, Long routeId, String routeName, TripStatus status,
            Instant scheduled, Instant planned, Instant started, Instant ended, Instant created, int attemptNumber,
            VehicleType vehicleType) {
        this(id,vehicleId,plate,routeId,routeName,status,scheduled,planned,started,ended,created,attemptNumber,vehicleType,null,null,
                TripDispatchMode.ON_DEMAND,null,null,null);
    }
    public static TripSummaryResponse from(TripEntity trip) {
        return from(trip, null);
    }
    public static TripSummaryResponse from(TripEntity trip, AssignmentRequestSummary assignmentRequest) {
        return new TripSummaryResponse(trip.getId(), trip.getVehicle().getId(), trip.getVehiclePlateSnapshot(),
                trip.getRoute().getId(), trip.getRoute().getName(), trip.getStatus(), trip.getScheduledDepartureAt(),
                plannedEndAt(trip),
                trip.getStartedAt(), trip.getEndedAt(), trip.getCreatedAt(), trip.getAttemptNumber(),
                trip.getVehicle().getVehicleType(), trip.getDriver() == null ? null : new DriverSnapshotResponse(
                        trip.getDriver().getId(), trip.getDriverNameSnapshot(), trip.getDriverPhoneSnapshot(),
                        trip.getDriverLicenseNumberSnapshot()), trip.getCancellationReason(),
                trip.getSchedule() == null ? TripDispatchMode.ON_DEMAND : TripDispatchMode.FIXED_SCHEDULE,
                trip.getSchedule() == null ? null : trip.getSchedule().getId(),
                trip.getSchedule() == null ? null : trip.getSchedule().getName(), assignmentRequest);
    }

    private static Instant plannedEndAt(TripEntity trip) {
        return trip.getStops().stream()
                .max(Comparator.comparing(stop -> stop.getSequenceNumber()))
                // Live traffic refreshes plannedArrivalAt, but must not change the scheduled baseline.
                .map(stop -> trip.getScheduledDepartureAt().plusSeconds(stop.getArrivalOffsetSeconds()))
                .orElseGet(() -> trip.getScheduledDepartureAt()
                        .plusSeconds(trip.getRoute().getEstimatedTripDurationSeconds()));
    }
}
