package com.quangkhai.vehicletracking_backend.dashboard;

import com.quangkhai.vehicletracking_backend.dashboard.service.DashboardService;
import com.quangkhai.vehicletracking_backend.driver.repository.DriverRepository;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripNotificationRepository;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripOffRouteAlertStateRepository;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStopEntity;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {
    @Mock VehicleRepository vehicles;
    @Mock DriverRepository drivers;
    @Mock TripRepository trips;
    @Mock TripOffRouteAlertStateRepository offRouteStates;
    @Mock TripNotificationRepository notifications;
    @Mock Clock operationsClock;
    @InjectMocks DashboardService service;

    @Test
    void summaryCombinesOperationalCountsAndPendingAlerts() {
        Instant now = Instant.parse("2026-09-21T08:00:00Z");
        when(operationsClock.instant()).thenReturn(now);
        when(vehicles.countByActiveTrue()).thenReturn(4L);
        when(drivers.countByActiveTrue()).thenReturn(3L);
        when(trips.findAllByStatus(TripStatus.IN_PROGRESS)).thenReturn(List.of());
        when(trips.countByStatus(TripStatus.IN_PROGRESS)).thenReturn(2L);
        when(trips.countByStatus(TripStatus.SCHEDULED)).thenReturn(5L);
        when(trips.countByStatus(TripStatus.COMPLETED)).thenReturn(8L);
        when(trips.countByStatus(TripStatus.CANCELLED)).thenReturn(1L);
        when(offRouteStates.countActiveForStatus(TripStatus.IN_PROGRESS)).thenReturn(1L);
        when(notifications.countByReadAtIsNullAndDismissedAtIsNull()).thenReturn(2L);
        when(notifications.findTop50ByReadAtIsNullAndDismissedAtIsNullOrderByCreatedAtDescIdDesc()).thenReturn(List.of());

        var summary = service.summary();

        assertThat(summary.serverTime()).isEqualTo(now);
        assertThat(summary.activeVehicleCount()).isEqualTo(4L);
        assertThat(summary.activeDriverCount()).isEqualTo(3L);
        assertThat(summary.tripsInProgress()).isEqualTo(2L);
        assertThat(summary.scheduledTrips()).isEqualTo(5L);
        assertThat(summary.completedTrips()).isEqualTo(8L);
        assertThat(summary.cancelledTrips()).isEqualTo(1L);
        assertThat(summary.overdueTrips()).isZero();
        assertThat(summary.offRouteVehicleCount()).isEqualTo(1L);
        assertThat(summary.unreadAlertCount()).isEqualTo(2L);
        assertThat(summary.pendingAlerts()).isEmpty();
    }

    @Test
    void summaryCountsOnlyRunningTripsPastPlannedEndAsOverdue() {
        Instant now = Instant.parse("2026-09-21T08:00:00Z");
        var overdueTrip = org.mockito.Mockito.mock(TripEntity.class);
        var finalStop = org.mockito.Mockito.mock(TripStopEntity.class);
        when(operationsClock.instant()).thenReturn(now);
        when(trips.findAllByStatus(TripStatus.IN_PROGRESS)).thenReturn(List.of(overdueTrip));
        when(finalStop.getPlannedArrivalAt()).thenReturn(now.minusSeconds(1));
        when(overdueTrip.getStops()).thenReturn(List.of(finalStop));
        when(trips.countByStatus(TripStatus.IN_PROGRESS)).thenReturn(1L);
        when(trips.countByStatus(TripStatus.SCHEDULED)).thenReturn(0L);
        when(trips.countByStatus(TripStatus.COMPLETED)).thenReturn(0L);
        when(trips.countByStatus(TripStatus.CANCELLED)).thenReturn(0L);
        when(notifications.findTop50ByReadAtIsNullAndDismissedAtIsNullOrderByCreatedAtDescIdDesc()).thenReturn(List.of());

        var summary = service.summary();

        assertThat(summary.overdueTrips()).isEqualTo(1L);
    }
}
