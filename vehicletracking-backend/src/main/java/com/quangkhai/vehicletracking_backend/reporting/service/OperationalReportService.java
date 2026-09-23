package com.quangkhai.vehicletracking_backend.reporting.service;

import com.quangkhai.vehicletracking_backend.config.ReportingProperties;
import com.quangkhai.vehicletracking_backend.reroute.entity.NotificationType;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripNotificationRepository;
import com.quangkhai.vehicletracking_backend.telemetry.entity.TelemetrySampleEntity;
import com.quangkhai.vehicletracking_backend.telemetry.entity.TelemetrySource;
import com.quangkhai.vehicletracking_backend.telemetry.repository.TelemetryRepository;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.reporting.dto.OperationalReportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OperationalReportService {
    private final TripRepository trips;
    private final TelemetryRepository telemetry;
    private final TripNotificationRepository notifications;
    private final ReportingProperties properties;
    private final Clock operationsClock;

    @Transactional(readOnly = true)
    public OperationalReportResponse operations(LocalDate requestedFrom, LocalDate requestedTo,
                                                Long vehicleId, Long driverId) {
        LocalDate to = requestedTo == null ? today() : requestedTo;
        LocalDate from = requestedFrom == null ? to.minusDays(29) : requestedFrom;
        validate(from, to, vehicleId, driverId);

        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toExclusive = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant now = operationsClock.instant();
        List<TripEntity> matchingTrips = trips.findAllForOperationalReport(fromInstant, toExclusive, vehicleId, driverId);

        long totalDistanceMeters = matchingTrips.stream()
                .map(trip -> trip.getRoute())
                .filter(route -> route != null && route.getTotalDistanceMeters() != null)
                .mapToLong(route -> route.getTotalDistanceMeters())
                .sum();
        long completedTripCount = matchingTrips.stream()
                .filter(trip -> trip.getStatus() == TripStatus.COMPLETED)
                .count();
        long totalRunningSeconds = matchingTrips.stream()
                .mapToLong(trip -> runningSeconds(trip, now))
                .sum();
        long onTimeTripCount = matchingTrips.stream()
                .filter(trip -> trip.getStatus() == TripStatus.COMPLETED)
                .filter(trip -> trip.getEndedAt() != null && !trip.getEndedAt().isAfter(plannedEndAt(trip)))
                .count();
        long lateTripCount = matchingTrips.stream().filter(trip -> isLate(trip, now)).count();
        double onTimeRatePercent = completedTripCount == 0
                ? 0d
                : roundPercent((onTimeTripCount * 100d) / completedTripCount);

        List<Long> tripIds = matchingTrips.stream().map(trip -> trip.getId()).toList();
        long offRouteEventCount = tripIds.isEmpty()
                ? 0
                : notifications.countForOperationalReport(tripIds, NotificationType.OFF_ROUTE_DETECTED,
                        fromInstant, toExclusive, vehicleId, driverId);
        long overspeedEventCount = tripIds.isEmpty()
                ? 0
                : countOverspeedEvents(telemetry.findAllForOperationalReport(
                        tripIds, toExclusive, TelemetrySource.GPS), fromInstant);

        return new OperationalReportResponse(
                from, to, now, matchingTrips.size(), completedTripCount,
                totalDistanceMeters, totalRunningSeconds, onTimeRatePercent,
                lateTripCount, offRouteEventCount, overspeedEventCount,
                properties.getDefaultSpeedLimitKmh());
    }

    private LocalDate today() {
        return operationsClock.instant().atZone(ZoneOffset.UTC).toLocalDate();
    }

    private void validate(LocalDate from, LocalDate to, Long vehicleId, Long driverId) {
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from không được sau to.");
        }
        long rangeDays = ChronoUnit.DAYS.between(from, to) + 1;
        if (rangeDays > properties.getMaxRangeDays()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Khoảng thời gian báo cáo tối đa là " + properties.getMaxRangeDays() + " ngày.");
        }
        if (vehicleId != null && vehicleId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "vehicleId phải là số dương.");
        }
        if (driverId != null && driverId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "driverId phải là số dương.");
        }
    }

    private long runningSeconds(TripEntity trip, Instant now) {
        Instant started = trip.getStartedAt();
        if (started == null) return 0;
        Instant ended = trip.getEndedAt() == null ? now : trip.getEndedAt();
        if (!ended.isAfter(started)) return 0;
        return Duration.between(started, ended).toSeconds();
    }

    private boolean isLate(TripEntity trip, Instant now) {
        Instant plannedEnd = plannedEndAt(trip);
        return trip.getStatus() == TripStatus.COMPLETED
                ? trip.getEndedAt() != null && trip.getEndedAt().isAfter(plannedEnd)
                : trip.getStatus() == TripStatus.IN_PROGRESS && plannedEnd.isBefore(now);
    }

    private Instant plannedEndAt(TripEntity trip) {
        if (trip.getStops() != null && !trip.getStops().isEmpty()) {
            return trip.getStops().stream()
                    .max(java.util.Comparator.comparingInt(stop -> stop.getSequenceNumber()))
                    // Live ETA updates mutate plannedArrivalAt.  The original
                    // arrival offset is immutable and remains the reporting baseline.
                    .filter(stop -> stop.getArrivalOffsetSeconds() != null)
                    .map(stop -> trip.getScheduledDepartureAt().plusSeconds(stop.getArrivalOffsetSeconds()))
                    .orElseGet(() -> routeEndAt(trip));
        }
        return routeEndAt(trip);
    }

    private Instant routeEndAt(TripEntity trip) {
        long duration = trip.getRoute() == null || trip.getRoute().getEstimatedTripDurationSeconds() == null
                ? 0 : trip.getRoute().getEstimatedTripDurationSeconds();
        return trip.getScheduledDepartureAt().plusSeconds(duration);
    }

    /**
     * Samples before {@code from} establish the carry-in state.  A speed that
     * was already over the limit at midnight is not a new episode in this
     * reporting window.
     */
    private long countOverspeedEvents(List<TelemetrySampleEntity> samples, Instant from) {
        double limit = properties.getDefaultSpeedLimitKmh();
        long events = 0;
        Long currentTripId = null;
        int currentAttempt = -1;
        boolean wasOverLimit = false;
        for (TelemetrySampleEntity sample : samples) {
            boolean sameAttempt = currentTripId != null && sample.getTripId().equals(currentTripId)
                    && sample.getAttemptNumber() == currentAttempt;
            if (!sameAttempt) {
                currentTripId = sample.getTripId();
                currentAttempt = sample.getAttemptNumber();
                wasOverLimit = false;
            }
            boolean overLimit = sample.getSpeedKmh() > limit;
            if (!sample.getRecordedAt().isBefore(from) && overLimit && !wasOverLimit) events++;
            wasOverLimit = overLimit;
        }
        return events;
    }

    private double roundPercent(double value) {
        return Math.round(value * 100d) / 100d;
    }
}
