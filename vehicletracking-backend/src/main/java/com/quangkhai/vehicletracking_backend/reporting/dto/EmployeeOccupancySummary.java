package com.quangkhai.vehicletracking_backend.reporting.dto;

public record EmployeeOccupancySummary(
        long completedTripCount,
        long tripsWithCompleteBoardingData,
        long tripsMissingBoardingData,
        long tripsMissingSeatCapacity,
        long totalBoardings,
        Double averageBoardingsPerTrip,
        Double averageOnboard,
        Double seatUtilizationPercent) {
}
