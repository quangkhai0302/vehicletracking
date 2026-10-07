package com.quangkhai.vehicletracking_backend.reporting.dto;

import java.time.Instant;

public record OperationalReportIncidentDetail(
        String id,
        long tripId,
        String routeName,
        String vehiclePlateNumber,
        String driverName,
        String type,
        String severity,
        Instant occurredAt,
        String status,
        String detail) {
}
