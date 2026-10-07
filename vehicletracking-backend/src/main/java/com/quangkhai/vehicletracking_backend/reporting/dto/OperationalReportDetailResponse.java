package com.quangkhai.vehicletracking_backend.reporting.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Operational report with the aggregate and the breakdowns used by the
 * reports screen. Passenger counts are based on driver-confirmed boardings
 * recorded at each pickup stop during the current completed simulation attempt.
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
        String employeePassengerDataNote,
        EmployeeOccupancySummary employeeOccupancy,
        List<EmployeeOccupancyVehicleRow> employeeOccupancyByVehicle,
        List<EmployeeOccupancyDayRow> employeeOccupancyByDay,
        List<EmployeeOccupancyStationRow> employeeOccupancyByStation,
        List<EmployeeOccupancyTripRow> employeeOccupancyTrips,
        List<OperationalReportIncidentDetail> incidentDetails) {
}
