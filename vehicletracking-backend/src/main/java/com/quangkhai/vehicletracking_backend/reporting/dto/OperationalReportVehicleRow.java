package com.quangkhai.vehicletracking_backend.reporting.dto;

import java.util.List;

/** A vehicle-level breakdown for the selected operational report window. */
public record OperationalReportVehicleRow(
        Long vehicleId,
        String plateNumber,
        String vehicleName,
        long tripCount,
        long completedTripCount,
        long lateTripCount,
        long lateStopCount,
        long incidentCount,
        Long employeePassengerCount,
        List<OperationalReportVehicleTrip> trips) {
}
