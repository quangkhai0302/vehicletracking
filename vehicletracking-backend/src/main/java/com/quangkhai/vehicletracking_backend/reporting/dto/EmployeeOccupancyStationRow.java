package com.quangkhai.vehicletracking_backend.reporting.dto;

public record EmployeeOccupancyStationRow(
        Long stationId,
        String stationName,
        long visitCount,
        long totalBoardings,
        Double averageBoardingsPerVisit) {
}
