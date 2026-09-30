package com.quangkhai.vehicletracking_backend.dashboard.service;

import com.quangkhai.vehicletracking_backend.dashboard.dto.DashboardSummaryResponse;
import com.quangkhai.vehicletracking_backend.driver.repository.DriverRepository;
import com.quangkhai.vehicletracking_backend.reroute.dto.NotificationResponse;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripNotificationRepository;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripOffRouteAlertStateRepository;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final VehicleRepository vehicles;
    private final DriverRepository drivers;
    private final TripRepository trips;
    private final TripOffRouteAlertStateRepository offRouteStates;
    private final TripNotificationRepository notifications;
    private final Clock operationsClock;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DashboardSummaryResponse summary() {
        Instant now = operationsClock.instant().truncatedTo(ChronoUnit.MICROS);
        var inProgress = trips.findAllByStatus(TripStatus.IN_PROGRESS);
        long overdue = inProgress.stream()
                .filter(trip -> trip.getSchedule() != null)
                .filter(trip -> plannedEndAt(trip).isBefore(now))
                .count();
        var pendingAlerts = notifications.findTop50ByReadAtIsNullAndDismissedAtIsNullOrderByCreatedAtDescIdDesc().stream()
                .map(NotificationResponse::from).limit(5).toList();
        return new DashboardSummaryResponse(
                now,
                vehicles.countByActiveTrue(),
                drivers.countByActiveTrue(),
                trips.countByStatus(TripStatus.IN_PROGRESS),
                trips.countByStatus(TripStatus.SCHEDULED),
                trips.countByStatus(TripStatus.COMPLETED),
                trips.countByStatus(TripStatus.CANCELLED),
                overdue,
                offRouteStates.countActiveForStatus(TripStatus.IN_PROGRESS),
                notifications.countByReadAtIsNullAndDismissedAtIsNull(),
                pendingAlerts);
    }

    private Instant plannedEndAt(TripEntity trip) {
        return trip.getStops().stream()
                .max(Comparator.comparingInt(stop -> stop.getSequenceNumber()))
                .map(stop -> trip.getScheduledDepartureAt().plusSeconds(stop.getArrivalOffsetSeconds()))
                .orElseGet(() -> trip.getScheduledDepartureAt()
                        .plusSeconds(trip.getRoute().getEstimatedTripDurationSeconds()));
    }
}
