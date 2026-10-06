package com.quangkhai.vehicletracking_backend.reporting.dto;

/** A grouped operational incident count. */
public record OperationalReportIncidentRow(
        String type,
        String severity,
        long count) {
}
