package com.quangkhai.vehicletracking_backend.reporting.dto;

import java.time.LocalDate;

public record EmployeeOccupancyDayRow(
        LocalDate date,
        long completedTripCount,
        long confirmedTripCount,
        long totalBoardings,
        Double averageBoardingsPerTrip,
        Double seatUtilizationPercent) {
}
