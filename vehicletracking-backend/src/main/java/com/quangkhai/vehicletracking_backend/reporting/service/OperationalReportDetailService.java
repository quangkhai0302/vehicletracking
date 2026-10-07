package com.quangkhai.vehicletracking_backend.reporting.service;

import com.quangkhai.vehicletracking_backend.checkin.entity.TripStopVisitEntity;
import com.quangkhai.vehicletracking_backend.checkin.repository.TripStopVisitRepository;
import com.quangkhai.vehicletracking_backend.config.ReportingProperties;
import com.quangkhai.vehicletracking_backend.reporting.dto.OperationalReportDetailResponse;
import com.quangkhai.vehicletracking_backend.reporting.dto.OperationalReportIncidentDetail;
import com.quangkhai.vehicletracking_backend.reporting.dto.OperationalReportDriverRow;
import com.quangkhai.vehicletracking_backend.reporting.dto.OperationalReportDriverTrip;
import com.quangkhai.vehicletracking_backend.reporting.dto.OperationalReportIncidentRow;
import com.quangkhai.vehicletracking_backend.reporting.dto.OperationalReportLateStop;
import com.quangkhai.vehicletracking_backend.reporting.dto.OperationalReportResponse;
import com.quangkhai.vehicletracking_backend.reporting.dto.OperationalReportVehicleRow;
import com.quangkhai.vehicletracking_backend.reporting.dto.EmployeeOccupancySummary;
import com.quangkhai.vehicletracking_backend.reporting.dto.EmployeeOccupancyVehicleRow;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OperationalReportDetailService {
    private static final ZoneId REPORTING_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String OVER_SPEED = "OVERSPEED";
    private static final String INCIDENT_SEVERITY = "MAJOR";
    private static final String EMPLOYEE_DATA_NOTE =
            "Chưa có xác nhận số người lên tại các điểm đón của chuyến đã hoàn tất.";

    private final OperationalReportService operationalReports;
    private final TripRepository trips;
    private final TripStopVisitRepository visits;
    private final TripNotificationRepository notifications;
    private final TelemetryRepository telemetry;
    private final ReportingProperties properties;
    private final Clock operationsClock;

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public OperationalReportDetailResponse detail(LocalDate requestedFrom, LocalDate requestedTo,
                                                   Long vehicleId, Long driverId) {
        OperationalReportResponse summary = operationalReports.operations(requestedFrom, requestedTo,
                vehicleId, driverId);
        Instant from = summary.from().atStartOfDay(REPORTING_ZONE).toInstant();
        Instant toExclusive = summary.to().plusDays(1).atStartOfDay(REPORTING_ZONE).toInstant();
        Instant now = operationsClock.instant();
        List<TripEntity> matchingTrips = trips.findAllForOperationalReport(from, toExclusive, vehicleId, driverId)
                .stream().filter(trip -> trip.getStartedAt() != null).toList();
        Set<Long> lateTripIds = operationalReports.lateTripIds(matchingTrips, now);
        List<Long> tripIds = matchingTrips.stream().map(TripEntity::getId).toList();

        List<TripStopVisitEntity> matchingVisits = tripIds.isEmpty()
                ? List.of()
                : visits.findAllByTripIdInOrderByTripIdAscStopSequenceAsc(tripIds);
        Map<Long, Map<Integer, TripStopVisitEntity>> visitsByTrip = new HashMap<>();
        for (TripStopVisitEntity visit : matchingVisits) {
            visitsByTrip.computeIfAbsent(visit.getTrip().getId(), ignored -> new HashMap<>())
                    .put(visit.getStopSequence(), visit);
        }
        List<TripNotificationEntity> matchingNotifications = tripIds.isEmpty()
                ? List.of()
                : notifications.findAllForOperationalReport(tripIds, from, toExclusive);
        List<TelemetrySampleEntity> samples = tripIds.isEmpty()
                ? List.of()
                : telemetry.findAllForOperationalReport(tripIds, toExclusive).stream()
                        .filter(sample -> matchingTrips.stream().anyMatch(trip -> trip.getId().equals(sample.getTripId())
                                && trip.getAttemptNumber() == sample.getAttemptNumber())).toList();

        Map<Long, TripEntity> tripsById = matchingTrips.stream()
                .collect(LinkedHashMap::new, (map, trip) -> map.put(trip.getId(), trip), Map::putAll);
        Map<Long, Long> lateStopsByTrip = new HashMap<>();
        List<OperationalReportLateStop> lateStops = new ArrayList<>();
        for (TripStopVisitEntity visit : matchingVisits) {
            TripEntity trip = tripsById.get(visit.getTrip().getId());
            if (trip == null || visit.getActualArrivalAt() == null) continue;
            TripStopEntity stop = findStop(trip, visit.getStopSequence());
            if (stop == null || stop.getArrivalOffsetSeconds() == null) continue;
            boolean simulated = visit.getSimulatedArrivalAt() != null;
            Instant actual = simulated ? visit.getSimulatedArrivalAt() : visit.getActualArrivalAt();
            Instant planned = (simulated ? trip.getStartedAt() : trip.getScheduledDepartureAt())
                    .plusSeconds(stop.getArrivalOffsetSeconds());
            if (!actual.isAfter(planned)) continue;
            long delay = Duration.between(planned, actual).toSeconds();
            lateStops.add(new OperationalReportLateStop(
                    trip.getId(), trip.getRoute() == null ? null : trip.getRoute().getName(),
                    trip.getVehiclePlateSnapshot(), driverName(trip), stop.getStationName(),
                    stop.getSequenceNumber(), planned, actual, delay));
            lateStopsByTrip.merge(trip.getId(), 1L, Long::sum);
        }
        lateStops.sort(Comparator.comparing(OperationalReportLateStop::actualArrivalAt).reversed());

        Map<Long, Long> incidentCountByTrip = new HashMap<>();
        Map<IncidentKey, Long> incidentGroups = new LinkedHashMap<>();
        List<OperationalReportIncidentDetail> incidentDetails = new ArrayList<>();
        for (TripNotificationEntity notification : matchingNotifications) {
            TripEntity trip = tripsById.get(notification.getTrip().getId());
            if (trip == null || (notification.getAttemptNumber() != null
                    && notification.getAttemptNumber() != trip.getAttemptNumber())) continue;
            var incident = notification.getSimulationIncident();
            if (notification.getType() == NotificationType.SIMULATION_INCIDENT) {
                if (incident == null || incident.getReportedByDriver() == null) continue;
                incidentDetails.add(new OperationalReportIncidentDetail("incident-" + incident.getId(), trip.getId(),
                        routeName(trip), trip.getVehiclePlateSnapshot(), incident.getReportedByDriver().getFullName(),
                        incident.getType().name(), incident.getSeverity().name(), incident.getCreatedAt(),
                        incident.getStatus().name(), incident.getDetail()));
            } else if (notification.getType() == NotificationType.OFF_ROUTE_DETECTED) {
                incidentDetails.add(new OperationalReportIncidentDetail("notification-" + notification.getId(), trip.getId(),
                        routeName(trip), trip.getVehiclePlateSnapshot(), driverName(trip),
                        notification.getType().name(), notification.getSeverity().name(), notification.getCreatedAt(),
                        "RECORDED", "Xe được ghi nhận đi lệch tuyến."));
            }
        }
        incidentDetails.addAll(overspeedDetails(samples, from, tripsById));
        incidentDetails.sort(Comparator.comparing(OperationalReportIncidentDetail::occurredAt).reversed());
        for (OperationalReportIncidentDetail incident : incidentDetails) {
            incidentCountByTrip.merge(incident.tripId(), 1L, Long::sum);
            incidentGroups.merge(new IncidentKey(incident.type(), incident.severity()), 1L, Long::sum);
        }

        Map<VehicleKey, MutableVehicle> vehicles = new LinkedHashMap<>();
        Map<DriverKey, MutableDriver> drivers = new LinkedHashMap<>();
        MutableOccupancy occupancy = new MutableOccupancy();
        Map<Long, MutableOccupancy> occupancyByVehicle = new LinkedHashMap<>();
        Map<Long, Long> confirmedBoardingsByTrip = new HashMap<>();
        for (TripEntity trip : matchingTrips) {
            boolean late = lateTripIds.contains(trip.getId());
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
            if (trip.getStatus() == TripStatus.COMPLETED) {
                OccupancyTrip counts = occupancyFor(trip, visitsByTrip.getOrDefault(trip.getId(), Map.of()));
                occupancy.addCompleted(counts, trip.getSeatCapacitySnapshot());
                occupancyByVehicle.computeIfAbsent(trip.getVehicle().getId(), ignored -> new MutableOccupancy())
                        .addCompleted(counts, trip.getSeatCapacitySnapshot());
                if (counts.complete()) confirmedBoardingsByTrip.put(trip.getId(), counts.boardings());
            }
        }

        return new OperationalReportDetailResponse(summary.from(), summary.to(), summary.generatedAt(), summary,
                vehicles.values().stream().map(row -> row.toRow(confirmedBoardingsByTrip)).toList(),
                drivers.values().stream().map(row -> row.toRow(confirmedBoardingsByTrip)).toList(),
                lateStops,
                incidentGroups.entrySet().stream()
                        .map(entry -> new OperationalReportIncidentRow(entry.getKey().type(), entry.getKey().severity(),
                                entry.getValue()))
                        .toList(),
                occupancy.completedTripsWithBoardings > 0,
                occupancy.completedTripsWithBoardings > 0 ? "Số liệu dựa trên số người tài xế xác nhận tại các điểm đón trong mô phỏng." : EMPLOYEE_DATA_NOTE,
                occupancy.summary(),
                vehicles.values().stream().map(row -> {
                    MutableOccupancy passengerStats = occupancyByVehicle.getOrDefault(row.key.id(), new MutableOccupancy());
                    return passengerStats.toVehicleRow(row.key.id(), row.key.plate(), row.key.name(),
                            matchingTrips.stream().filter(t -> t.getVehicle().getId().equals(row.key.id()))
                                    .map(t -> t.getVehicle().getSeatCapacity()).filter(java.util.Objects::nonNull)
                                    .findFirst().orElse(null));
                }).toList(), incidentDetails);
    }

    private OccupancyTrip occupancyFor(TripEntity trip, Map<Integer, TripStopVisitEntity> visitsBySequence) {
        List<TripStopEntity> stops = trip.getStops() == null ? List.of() : trip.getStops().stream()
                .sorted(Comparator.comparingInt(TripStopEntity::getSequenceNumber)).toList();
        if (stops.size() < 2) return new OccupancyTrip(false, 0);
        long boardings = 0;
        if (!visitsBySequence.containsKey(stops.getLast().getSequenceNumber()))
            return new OccupancyTrip(false, 0);
        for (int i = 0; i < stops.size() - 1; i++) {
            TripStopVisitEntity visit = visitsBySequence.get(stops.get(i).getSequenceNumber());
            if (visit == null || visit.getEmployeeBoardingCount() == null)
                return new OccupancyTrip(false, 0);
            boardings += visit.getEmployeeBoardingCount();
        }
        return new OccupancyTrip(true, boardings);
    }

    private String routeName(TripEntity trip) {
        return trip.getRoute() == null ? null : trip.getRoute().getName();
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

    private List<OperationalReportIncidentDetail> overspeedDetails(List<TelemetrySampleEntity> samples,
                                                                  Instant from, Map<Long, TripEntity> tripsById) {
        List<OperationalReportIncidentDetail> result = new ArrayList<>();
        Long currentTripId = null;
        int currentAttempt = -1;
        TelemetrySource currentSource = null;
        boolean wasOverLimit = false;
        double limit = properties.getDefaultSpeedLimitKmh();
        for (TelemetrySampleEntity sample : samples) {
            boolean sameAttempt = sample.getTripId().equals(currentTripId)
                    && sample.getAttemptNumber() == currentAttempt && sample.getSource() == currentSource;
            if (!sameAttempt) {
                currentTripId = sample.getTripId();
                currentAttempt = sample.getAttemptNumber();
                currentSource = sample.getSource();
                wasOverLimit = false;
            }
            boolean overLimit = sample.getSpeedKmh() > limit;
            if (!sample.getRecordedAt().isBefore(from) && overLimit && !wasOverLimit) {
                TripEntity trip = tripsById.get(sample.getTripId());
                if (trip != null) result.add(new OperationalReportIncidentDetail("speed-" + sample.getId(),
                        trip.getId(), routeName(trip), trip.getVehiclePlateSnapshot(), driverName(trip),
                        OVER_SPEED, INCIDENT_SEVERITY, sample.getRecordedAt(), "RECORDED",
                        "Tốc độ vượt giới hạn " + limit + " km/h."));
            }
            wasOverLimit = overLimit;
        }
        return result;
    }

    private record IncidentKey(String type, String severity) { }
    private record VehicleKey(Long id, String plate, String name) { }
    private record DriverKey(Long id, String name) { }
    private record OccupancyTrip(boolean complete, long boardings) { }

    private static final class MutableOccupancy {
        private long completedTrips;
        private long completedTripsWithBoardings;
        private long completedTripsMissingCapacity;
        private long totalBoardings;
        private long boardingsWithCapacity;
        private long seats;

        private void addCompleted(OccupancyTrip trip, Integer capacity) {
            completedTrips++;
            if (capacity == null) completedTripsMissingCapacity++;
            if (!trip.complete()) return;
            completedTripsWithBoardings++;
            totalBoardings += trip.boardings();
            if (capacity != null) {
                boardingsWithCapacity += trip.boardings();
                seats += capacity;
            }
        }

        private EmployeeOccupancySummary summary() {
            Double averageBoardings = completedTripsWithBoardings == 0 ? null
                    : (double) totalBoardings / completedTripsWithBoardings;
            Double utilization = seats == 0 ? null : 100d * boardingsWithCapacity / seats;
            return new EmployeeOccupancySummary(completedTrips, completedTripsWithBoardings,
                    completedTrips - completedTripsWithBoardings, completedTripsMissingCapacity,
                    totalBoardings, averageBoardings, null, utilization);
        }

        private EmployeeOccupancyVehicleRow toVehicleRow(Long vehicleId, String plate, String name, Integer capacity) {
            Double averageBoardings = completedTripsWithBoardings == 0 ? null
                    : (double) totalBoardings / completedTripsWithBoardings;
            Double utilization = seats == 0 ? null : 100d * boardingsWithCapacity / seats;
            return new EmployeeOccupancyVehicleRow(vehicleId, plate, name, capacity, completedTrips,
                    completedTripsWithBoardings, completedTrips - completedTripsWithBoardings,
                    totalBoardings, averageBoardings, null, utilization);
        }
    }

    private static final class MutableVehicle {
        private final VehicleKey key;
        private long trips;
        private long completed;
        private long late;
        private long lateStops;
        private long incidents;
        private final List<Long> completedTripIds = new ArrayList<>();

        private MutableVehicle(VehicleKey key) { this.key = key; }
        private void add(TripEntity trip, boolean isLate, long lateStopCount, long incidentCount) {
            trips++;
            if (trip.getStatus() == TripStatus.COMPLETED) { completed++; completedTripIds.add(trip.getId()); }
            if (isLate) late++;
            lateStops += lateStopCount;
            incidents += incidentCount;
        }
        private OperationalReportVehicleRow toRow(Map<Long, Long> boardingsByTrip) {
            Long passengers = completedTripIds.stream().filter(boardingsByTrip::containsKey)
                    .mapToLong(boardingsByTrip::get).sum();
            if (completedTripIds.stream().noneMatch(boardingsByTrip::containsKey)) passengers = null;
            return new OperationalReportVehicleRow(key.id(), key.plate(), key.name(), trips, completed, late,
                    lateStops, incidents, passengers);
        }
    }

    private static final class MutableDriver {
        private final DriverKey key;
        private long trips;
        private long completed;
        private long late;
        private long lateStops;
        private long incidents;
        private final List<Long> completedTripIds = new ArrayList<>();
        private final List<OperationalReportDriverTrip> tripDetails = new ArrayList<>();

        private MutableDriver(DriverKey key) { this.key = key; }
        private void add(TripEntity trip, boolean isLate, long lateStopCount, long incidentCount) {
            trips++;
            tripDetails.add(new OperationalReportDriverTrip(trip.getId(),
                    trip.getRoute() == null ? null : trip.getRoute().getName(),
                    trip.getVehiclePlateSnapshot(), trip.getScheduledDepartureAt(),
                    trip.getStartedAt(), trip.getEndedAt(), trip.getStatus()));
            if (trip.getStatus() == TripStatus.COMPLETED) { completed++; completedTripIds.add(trip.getId()); }
            if (isLate) late++;
            lateStops += lateStopCount;
            incidents += incidentCount;
        }
        private OperationalReportDriverRow toRow(Map<Long, Long> boardingsByTrip) {
            Long passengers = completedTripIds.stream().filter(boardingsByTrip::containsKey)
                    .mapToLong(boardingsByTrip::get).sum();
            if (completedTripIds.stream().noneMatch(boardingsByTrip::containsKey)) passengers = null;
            return new OperationalReportDriverRow(key.id(), key.name(), trips, completed, late,
                    lateStops, incidents, passengers,
                    tripDetails.stream().sorted(Comparator.comparing(OperationalReportDriverTrip::startedAt).reversed()
                            .thenComparing(OperationalReportDriverTrip::tripId, Comparator.reverseOrder())).toList());
        }
    }
}
