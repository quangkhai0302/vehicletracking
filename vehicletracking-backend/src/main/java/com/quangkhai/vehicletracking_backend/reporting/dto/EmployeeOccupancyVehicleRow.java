package com.quangkhai.vehicletracking_backend.reporting.dto;

public record EmployeeOccupancyVehicleRow(
        Long vehicleId,
        String plateNumber,
        String vehicleName,
        Integer seatCapacity,
        long completedTripCount,
        long tripsWithCompleteBoardingData,
        long tripsMissingBoardingData,
        long totalBoardings,
        Double averageBoardingsPerTrip,
        Double averageOnboard,
        Double seatUtilizationPercent) {
}
