package com.quangkhai.vehicletracking_backend.reporting.service;

import com.quangkhai.vehicletracking_backend.checkin.entity.TripStopVisitEntity;
import com.quangkhai.vehicletracking_backend.checkin.repository.TripStopVisitRepository;
import com.quangkhai.vehicletracking_backend.config.ReportingProperties;
import com.quangkhai.vehicletracking_backend.reporting.dto.OperationalReportDetailResponse;
import com.quangkhai.vehicletracking_backend.reporting.dto.OperationalReportDriverRow;
import com.quangkhai.vehicletracking_backend.reporting.dto.OperationalReportIncidentRow;
import com.quangkhai.vehicletracking_backend.reporting.dto.OperationalReportLateStop;
import com.quangkhai.vehicletracking_backend.reporting.dto.OperationalReportResponse;
import com.quangkhai.vehicletracking_backend.reporting.dto.OperationalReportVehicleRow;
import com.quangkhai.vehicletracking_backend.reroute.entity.TripNotificationEntity;
import com.quangkhai.vehicletracking_backend.reroute.entity.NotificationType;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripNotificationRepository;
import com.quangkhai.vehicletracking_backend.telemetry.entity.TelemetrySampleEntity;
import com.quangkhai.vehicletracking_backend.telemetry.entity.TelemetrySource;
import com.quangkhai.vehicletracking_backend.telemetry.repository.TelemetryRepository;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStopEntity;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OperationalReportDetailService {
    private static final String OVER_SPEED = "OVERSPEED";
    private static final String INCIDENT_SEVERITY = "MAJOR";
    private static final String EMPLOYEE_DATA_NOTE =
            "Chưa có dữ liệu số nhân viên/hành khách vì chuyến chưa lưu danh sách người đi xe.";

    private final OperationalReportService operationalReports;
    private final TripRepository trips;
    private final TripStopVisitRepository visits;
    private final TripNotificationRepository notifications;
    private final TelemetryRepository telemetry;
    private final ReportingProperties properties;
    private final Clock operationsClock;

    @Transactional(readOnly = true)
    public OperationalReportDetailResponse detail(LocalDate requestedFrom, LocalDate requestedTo,
                                                   Long vehicleId, Long driverId) {
        OperationalReportResponse summary = operationalReports.operations(requestedFrom, requestedTo,
                vehicleId, driverId);
        Instant from = summary.from().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toExclusive = summary.to().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant now = operationsClock.instant();
        List<TripEntity> matchingTrips = trips.findAllForOperationalReport(from, toExclusive, vehicleId, driverId);
        List<Long> tripIds = matchingTrips.stream().map(TripEntity::getId).toList();

        List<TripStopVisitEntity> matchingVisits = tripIds.isEmpty()
                ? List.of()
                : visits.findAllByTripIdInOrderByTripIdAscStopSequenceAsc(tripIds);
        List<TripNotificationEntity> matchingNotifications = tripIds.isEmpty()
                ? List.of()
                : notifications.findAllForOperationalReport(tripIds, from, toExclusive);
        List<TelemetrySampleEntity> samples = tripIds.isEmpty()
                ? List.of()
                : telemetry.findAllForOperationalReport(tripIds, toExclusive, TelemetrySource.GPS);

        Map<Long, TripEntity> tripsById = matchingTrips.stream()
                .collect(LinkedHashMap::new, (map, trip) -> map.put(trip.getId(), trip), Map::putAll);
        Map<Long, Long> lateStopsByTrip = new HashMap<>();
        List<OperationalReportLateStop> lateStops = new ArrayList<>();
        for (TripStopVisitEntity visit : matchingVisits) {
            TripEntity trip = tripsById.get(visit.getTrip().getId());
            if (trip == null || visit.getActualArrivalAt() == null) continue;
            TripStopEntity stop = findStop(trip, visit.getStopSequence());
            if (stop == null || stop.getArrivalOffsetSeconds() == null) continue;
            Instant planned = trip.getScheduledDepartureAt().plusSeconds(stop.getArrivalOffsetSeconds());
            if (!visit.getActualArrivalAt().isAfter(planned)) continue;
            long delay = Math.max(0, Duration.between(planned, visit.getActualArrivalAt()).toSeconds());
            lateStops.add(new OperationalReportLateStop(
                    trip.getId(), trip.getRoute() == null ? null : trip.getRoute().getName(),
                    trip.getVehiclePlateSnapshot(), driverName(trip), stop.getStationName(),
                    stop.getSequenceNumber(), planned, visit.getActualArrivalAt(), delay));
            lateStopsByTrip.merge(trip.getId(), 1L, Long::sum);
        }
        lateStops.sort(Comparator.comparing(OperationalReportLateStop::actualArrivalAt).reversed());

        Map<Long, Long> incidentCountByTrip = new HashMap<>();
        Map<IncidentKey, Long> incidentGroups = new LinkedHashMap<>();
        for (TripNotificationEntity notification : matchingNotifications) {
            if (!isIncident(notification.getType())) continue;
            incidentCountByTrip.merge(notification.getTrip().getId(), 1L, Long::sum);
            incidentGroups.merge(new IncidentKey(notification.getType().name(), notification.getSeverity().name()),
                    1L, Long::sum);
        }
        Map<Long, Long> overspeedByTrip = countOverspeedByTrip(samples, from);
        overspeedByTrip.forEach((tripId, count) -> {
            incidentCountByTrip.merge(tripId, count, Long::sum);
            incidentGroups.merge(new IncidentKey(OVER_SPEED, INCIDENT_SEVERITY), count, Long::sum);
        });

        Map<VehicleKey, MutableVehicle> vehicles = new LinkedHashMap<>();
        Map<DriverKey, MutableDriver> drivers = new LinkedHashMap<>();
        for (TripEntity trip : matchingTrips) {
            boolean late = isLate(trip, now);
            long lateStopCount = lateStopsByTrip.getOrDefault(trip.getId(), 0L);
            long incidentCount = incidentCountByTrip.getOrDefault(trip.getId(), 0L);
            VehicleKey vehicleKey = new VehicleKey(trip.getVehicle().getId(), trip.getVehiclePlateSnapshot(),
                    trip.getVehicle().getName());
            vehicles.computeIfAbsent(vehicleKey, ignored -> new MutableVehicle(vehicleKey))
                    .add(trip, late, lateStopCount, incidentCount);

            DriverKey driverKey = new DriverKey(trip.getDriver() == null ? null : trip.getDriver().getId(),
                    driverName(trip));
            drivers.computeIfAbsent(driverKey, ignored -> new MutableDriver(driverKey))
                    .add(trip, late, lateStopCount, incidentCount);
        }

        return new OperationalReportDetailResponse(summary.from(), summary.to(), summary.generatedAt(), summary,
                vehicles.values().stream().map(MutableVehicle::toRow).toList(),
                drivers.values().stream().map(MutableDriver::toRow).toList(),
                lateStops,
                incidentGroups.entrySet().stream()
                        .map(entry -> new OperationalReportIncidentRow(entry.getKey().type(), entry.getKey().severity(),
                                entry.getValue()))
                        .toList(),
                false,
                EMPLOYEE_DATA_NOTE);
    }

    private boolean isIncident(NotificationType type) {
        return type != null && type != NotificationType.TRIP_AUTO_STARTED;
    }

    private TripStopEntity findStop(TripEntity trip, int sequence) {
        return trip.getStops() == null ? null : trip.getStops().stream()
                .filter(stop -> stop.getSequenceNumber() != null && stop.getSequenceNumber() == sequence)
                .findFirst().orElse(null);
    }

    private String driverName(TripEntity trip) {
        if (trip.getDriver() != null && trip.getDriver().getFullName() != null) {
            return trip.getDriver().getFullName();
        }
        return trip.getDriverNameSnapshot();
    }

    private boolean isLate(TripEntity trip, Instant now) {
        if (trip.getSchedule() == null) return false;
        Instant plannedEnd = plannedEndAt(trip);
        return trip.getStatus() == TripStatus.COMPLETED
                ? trip.getEndedAt() != null && trip.getEndedAt().isAfter(plannedEnd)
                : trip.getStatus() == TripStatus.IN_PROGRESS && plannedEnd.isBefore(now);
    }

    private Instant plannedEndAt(TripEntity trip) {
        if (trip.getStops() != null && !trip.getStops().isEmpty()) {
            return trip.getStops().stream()
                    .max(Comparator.comparingInt(stop -> stop.getSequenceNumber()))
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

    private Map<Long, Long> countOverspeedByTrip(List<TelemetrySampleEntity> samples, Instant from) {
        Map<Long, Long> result = new HashMap<>();
        Long currentTripId = null;
        int currentAttempt = -1;
        boolean wasOverLimit = false;
        double limit = properties.getDefaultSpeedLimitKmh();
        for (TelemetrySampleEntity sample : samples) {
            boolean sameAttempt = currentTripId != null && sample.getTripId().equals(currentTripId)
                    && sample.getAttemptNumber() == currentAttempt;
            if (!sameAttempt) {
                currentTripId = sample.getTripId();
                currentAttempt = sample.getAttemptNumber();
                wasOverLimit = false;
            }
            boolean overLimit = sample.getSpeedKmh() > limit;
            if (!sample.getRecordedAt().isBefore(from) && overLimit && !wasOverLimit) {
                result.merge(sample.getTripId(), 1L, Long::sum);
            }
            wasOverLimit = overLimit;
        }
        return result;
    }

    private record IncidentKey(String type, String severity) { }
    private record VehicleKey(Long id, String plate, String name) { }
    private record DriverKey(Long id, String name) { }

    private static final class MutableVehicle {
        private final VehicleKey key;
        private long trips;
        private long completed;
        private long late;
        private long lateStops;
        private long incidents;

        private MutableVehicle(VehicleKey key) { this.key = key; }
        private void add(TripEntity trip, boolean isLate, long lateStopCount, long incidentCount) {
            trips++;
            if (trip.getStatus() == TripStatus.COMPLETED) completed++;
            if (isLate) late++;
            lateStops += lateStopCount;
            incidents += incidentCount;
        }
        private OperationalReportVehicleRow toRow() {
            return new OperationalReportVehicleRow(key.id(), key.plate(), key.name(), trips, completed, late,
                    lateStops, incidents, null);
        }
    }

    private static final class MutableDriver {
        private final DriverKey key;
        private long trips;
        private long completed;
        private long late;
        private long lateStops;
        private long incidents;

        private MutableDriver(DriverKey key) { this.key = key; }
        private void add(TripEntity trip, boolean isLate, long lateStopCount, long incidentCount) {
            trips++;
            if (trip.getStatus() == TripStatus.COMPLETED) completed++;
            if (isLate) late++;
            lateStops += lateStopCount;
            incidents += incidentCount;
        }
        private OperationalReportDriverRow toRow() {
            return new OperationalReportDriverRow(key.id(), key.name(), trips, completed, late,
                    lateStops, incidents, null);
        }
    }
}
