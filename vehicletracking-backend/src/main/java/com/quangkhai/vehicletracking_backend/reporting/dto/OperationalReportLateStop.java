package com.quangkhai.vehicletracking_backend.reporting.dto;

import java.time.Instant;

/** A stop visit that arrived after its immutable planned arrival baseline. */
public record OperationalReportLateStop(
        Long tripId,
        String routeName,
        String vehiclePlateNumber,
        String driverName,
        String stationName,
        int stopSequence,
        Instant plannedArrivalAt,
        Instant actualArrivalAt,
        long delaySeconds) {
}
