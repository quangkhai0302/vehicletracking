package com.quangkhai.vehicletracking_backend.reporting.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Operational report with the aggregate and the breakdowns used by the
 * reports screen. Employee/passenger counts are nullable until a manifest
 * source is introduced into the domain model.
 */
public record OperationalReportDetailResponse(
        LocalDate from,
        LocalDate to,
        Instant generatedAt,
        OperationalReportResponse summary,
        List<OperationalReportVehicleRow> vehicles,
        List<OperationalReportDriverRow> drivers,
        List<OperationalReportLateStop> lateStops,
        List<OperationalReportIncidentRow> incidents,
        boolean employeePassengerDataAvailable,
        String employeePassengerDataNote) {
}
