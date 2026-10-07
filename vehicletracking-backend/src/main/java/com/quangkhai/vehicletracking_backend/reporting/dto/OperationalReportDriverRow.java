package com.quangkhai.vehicletracking_backend.reporting.dto;

import java.util.List;

/** A driver-level breakdown for the selected operational report window. */
public record OperationalReportDriverRow(
        Long driverId,
        String driverName,
        long tripCount,
        long completedTripCount,
        long lateTripCount,
        long lateStopCount,
        long incidentCount,
        Long employeePassengerCount,
        List<OperationalReportDriverTrip> trips) {
}
