package com.quangkhai.vehicletracking_backend.reporting.dto;

/** A driver-level breakdown for the selected operational report window. */
public record OperationalReportDriverRow(
        Long driverId,
        String driverName,
        long tripCount,
        long completedTripCount,
        long lateTripCount,
        long lateStopCount,
        long incidentCount,
        Long employeePassengerCount) {
}
