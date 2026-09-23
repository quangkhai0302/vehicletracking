package com.quangkhai.vehicletracking_backend.reporting.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Historical operational aggregates for the selected planned departure dates.
 * Distances are planned route distances; running time uses actual trip
 * timestamps when available.
 */
public record OperationalReportResponse(
        LocalDate from,
        LocalDate to,
        Instant generatedAt,
        long tripCount,
        long completedTripCount,
        long totalDistanceMeters,
        long totalRunningSeconds,
        double onTimeRatePercent,
        long lateTripCount,
        long offRouteEventCount,
        long overspeedEventCount,
        double speedLimitKmh) {
}
