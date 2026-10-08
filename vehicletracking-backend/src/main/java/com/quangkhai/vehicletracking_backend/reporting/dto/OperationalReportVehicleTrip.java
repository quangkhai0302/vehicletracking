package com.quangkhai.vehicletracking_backend.reporting.dto;

import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;

import java.time.Instant;

/** A started trip contributing to one vehicle row in the current report window. */
public record OperationalReportVehicleTrip(
        Long tripId,
        String routeName,
        String driverName,
        Instant scheduledDepartureAt,
        Instant startedAt,
        Instant endedAt,
        TripStatus status) {
}
