package com.quangkhai.vehicletracking_backend.reporting.dto;

public record EmployeeOccupancyStopRow(
        Long stationId,
        String stationName,
        int stopSequence,
        Integer boardingCount,
        Long onboardAfterStop) {
}
