package com.quangkhai.vehicletracking_backend.reporting.dto;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
public record SimulationReportResponse(LocalDate from, LocalDate to, Instant generatedAt,
        long attemptCount, long completedAttemptCount, long knownCompletedAttemptCount,
        double totalPlannedDistanceMeters, double totalVirtualSeconds, Double onTimeRatePercent,
        long lateAttemptCount, long offRouteEventCount, long unknownAttemptCount,
        List<SimulationReportItem> items, int page, int size, long totalElements, int totalPages) {}

