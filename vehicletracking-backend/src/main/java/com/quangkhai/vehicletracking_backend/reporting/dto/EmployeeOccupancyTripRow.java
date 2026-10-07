package com.quangkhai.vehicletracking_backend.reporting.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record EmployeeOccupancyTripRow(
        Long tripId,
        String routeName,
        String vehiclePlateNumber,
        String driverName,
        LocalDate serviceDate,
        Instant scheduledDepartureAt,
        Integer seatCapacity,
        boolean complete,
        Long totalBoardings,
        List<EmployeeOccupancyStopRow> pickupStops) {
}
