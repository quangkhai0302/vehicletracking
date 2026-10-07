package com.quangkhai.vehicletracking_backend.reporting.dto;

import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;

import java.time.Instant;

/** A started trip contributing to one driver row in the current report window. */
public record OperationalReportDriverTrip(
        Long tripId,
        String routeName,
        String vehiclePlateNumber,
        Instant scheduledDepartureAt,
        Instant startedAt,
        Instant endedAt,
        TripStatus status) {
}
