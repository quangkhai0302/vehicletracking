package com.quangkhai.vehicletracking_backend.reporting;

import com.quangkhai.vehicletracking_backend.config.ReportingProperties;
import com.quangkhai.vehicletracking_backend.reporting.service.OperationalReportService;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripNotificationRepository;
import com.quangkhai.vehicletracking_backend.route.entity.RouteEntity;
import com.quangkhai.vehicletracking_backend.schedule.entity.TripScheduleEntity;
import com.quangkhai.vehicletracking_backend.telemetry.entity.TelemetrySampleEntity;
import com.quangkhai.vehicletracking_backend.telemetry.repository.TelemetryRepository;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStopEntity;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationalReportServiceTest {
    @Mock TripRepository trips;
    @Mock TelemetryRepository telemetry;
    @Mock TripNotificationRepository notifications;
    @Mock RouteEntity route;
    @Mock Clock clock;
    @Mock com.quangkhai.vehicletracking_backend.simulation.repository.SimulationRepository simulations;

    @Test
    void aggregatesTripRuntimeOnTimeLateOffRouteAndOverspeedEvents() {
        ReportingProperties properties = new ReportingProperties();
        properties.setDefaultSpeedLimitKmh(80);
        OperationalReportService service = new OperationalReportService(trips, telemetry, notifications, properties, clock, simulations);
        Instant now = Instant.parse("2026-09-21T13:00:00Z");
        when(clock.instant()).thenReturn(now);
        when(route.getTotalDistanceMeters()).thenReturn(12_000L);
        when(route.getEstimatedTripDurationSeconds()).thenReturn(3_600L);

        TripEntity onTime = trip(1L, route, TripStatus.COMPLETED,
                "2026-09-21T08:00:00Z", "2026-09-21T08:45:00Z", "2026-09-21T08:00:00Z");
        TripEntity late = trip(2L, route, TripStatus.COMPLETED,
                "2026-09-21T09:00:00Z", "2026-09-21T10:10:00Z", "2026-09-21T09:00:00Z");
        TripEntity overdue = trip(3L, route, TripStatus.IN_PROGRESS,
                "2026-09-21T11:00:00Z", null, "2026-09-21T11:00:00Z");
        when(trips.findAllForOperationalReport(
                Instant.parse("2026-09-20T17:00:00Z"), Instant.parse("2026-09-21T17:00:00Z"), null, null))
                .thenReturn(List.of(onTime, late, overdue));
        when(notifications.countForOperationalReport(
                List.of(1L, 2L, 3L),
                com.quangkhai.vehicletracking_backend.reroute.entity.NotificationType.OFF_ROUTE_DETECTED,
                Instant.parse("2026-09-20T17:00:00Z"), Instant.parse("2026-09-21T17:00:00Z"), null, null))
                .thenReturn(2L);
        when(onTime.getAttemptNumber()).thenReturn(1);
        when(late.getAttemptNumber()).thenReturn(1);
        List<TelemetrySampleEntity> samples = List.of(sample(1L, 1, 70), sample(1L, 1, 90), sample(1L, 1, 95),
                sample(1L, 1, 70), sample(2L, 1, 81));
        when(telemetry.findAllForOperationalReport(
                List.of(1L, 2L, 3L), Instant.parse("2026-09-21T17:00:00Z")))
                .thenReturn(samples);

        var result = service.operations(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 21), null, null);

        assertThat(result.tripCount()).isEqualTo(3);
        assertThat(result.completedTripCount()).isEqualTo(2);
        assertThat(result.totalDistanceMeters()).isEqualTo(36_000);
        assertThat(result.totalRunningSeconds()).isEqualTo(2_700 + 4_200 + 7_200);
        assertThat(result.onTimeRatePercent()).isEqualTo(50d);
        assertThat(result.lateTripCount()).isEqualTo(2);
        assertThat(result.offRouteEventCount()).isEqualTo(2);
        assertThat(result.overspeedEventCount()).isEqualTo(2);
    }

    @Test
    void usesOriginalStopOffsetWhenLiveEtaWasUpdated() {
        ReportingProperties properties = new ReportingProperties();
        OperationalReportService service = new OperationalReportService(trips, telemetry, notifications, properties, clock, simulations);
        Instant now = Instant.parse("2026-09-21T12:00:00Z");
        when(clock.instant()).thenReturn(now);
        when(route.getTotalDistanceMeters()).thenReturn(10_000L);
        TripEntity trip = org.mockito.Mockito.mock(TripEntity.class);
        when(trip.getId()).thenReturn(9L);
        when(trip.getRoute()).thenReturn(route);
        when(trip.getStatus()).thenReturn(TripStatus.COMPLETED);
        when(trip.getSchedule()).thenReturn(org.mockito.Mockito.mock(TripScheduleEntity.class));
        when(trip.getStartedAt()).thenReturn(Instant.parse("2026-09-21T08:00:00Z"));
        when(trip.getEndedAt()).thenReturn(Instant.parse("2026-09-21T08:30:00Z"));
        when(trip.getScheduledDepartureAt()).thenReturn(Instant.parse("2026-09-21T08:00:00Z"));
        TripStopEntity finalStop = org.mockito.Mockito.mock(TripStopEntity.class);
        when(finalStop.getArrivalOffsetSeconds()).thenReturn(3_600L);
        when(trip.getStops()).thenReturn(List.of(finalStop));
        when(trips.findAllForOperationalReport(
                Instant.parse("2026-09-20T17:00:00Z"), Instant.parse("2026-09-21T17:00:00Z"), null, null))
                .thenReturn(List.of(trip));

        var result = service.operations(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 21), null, null);

        assertThat(result.onTimeRatePercent()).isEqualTo(100d);
        assertThat(result.lateTripCount()).isZero();
    }

    @Test
    void excludesOnDemandTripsFromPunctualityAndLateMetrics() {
        ReportingProperties properties = new ReportingProperties();
        OperationalReportService service = new OperationalReportService(trips, telemetry, notifications, properties, clock, simulations);
        Instant now = Instant.parse("2026-09-21T12:00:00Z");
        Instant from = Instant.parse("2026-09-20T17:00:00Z");
        Instant toExclusive = Instant.parse("2026-09-21T17:00:00Z");
        TripEntity onDemandTrip = org.mockito.Mockito.mock(TripEntity.class);
        when(clock.instant()).thenReturn(now);
        when(route.getTotalDistanceMeters()).thenReturn(10_000L);
        when(onDemandTrip.getId()).thenReturn(10L);
        when(onDemandTrip.getRoute()).thenReturn(route);
        when(onDemandTrip.getStatus()).thenReturn(TripStatus.COMPLETED);
        when(onDemandTrip.getStartedAt()).thenReturn(Instant.parse("2026-09-21T08:00:00Z"));
        when(onDemandTrip.getEndedAt()).thenReturn(Instant.parse("2026-09-21T10:00:00Z"));
        when(trips.findAllForOperationalReport(from, toExclusive, null, null)).thenReturn(List.of(onDemandTrip));

        var result = service.operations(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 21), null, null);

        assertThat(result.tripCount()).isEqualTo(1);
        assertThat(result.completedTripCount()).isEqualTo(1);
        assertThat(result.totalRunningSeconds()).isEqualTo(7_200);
        assertThat(result.onTimeRatePercent()).isZero();
        assertThat(result.lateTripCount()).isZero();
    }

    @Test
    void rejectsInvalidDateAndFilterRange() {
        ReportingProperties properties = new ReportingProperties();
        OperationalReportService service = new OperationalReportService(trips, telemetry, notifications, properties, clock, simulations);

        assertThatThrownBy(() -> service.operations(LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1), null, null))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> service.operations(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), 0L, null))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> service.operations(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 2), null, null))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void doesNotCountCarryInOverspeedAsNewEpisodeAtReportBoundary() {
        ReportingProperties properties = new ReportingProperties();
        properties.setDefaultSpeedLimitKmh(80);
        OperationalReportService service = new OperationalReportService(trips, telemetry, notifications, properties, clock, simulations);
        Instant from = Instant.parse("2026-09-20T17:00:00Z");
        Instant toExclusive = Instant.parse("2026-09-21T17:00:00Z");
        when(clock.instant()).thenReturn(Instant.parse("2026-09-21T12:00:00Z"));
        TripEntity trip = trip(4L, route, TripStatus.COMPLETED,
                "2026-09-21T01:00:00Z", "2026-09-21T02:00:00Z", "2026-09-21T01:00:00Z");
        when(trips.findAllForOperationalReport(from, toExclusive, null, null)).thenReturn(List.of(trip));
        when(trip.getAttemptNumber()).thenReturn(1);
        List<TelemetrySampleEntity> samples = List.of(
                sample(4L, 1, 90, from.minusSeconds(1)),
                sample(4L, 1, 95, from.plusSeconds(1)),
                sample(4L, 1, 70, from.plusSeconds(2)),
                sample(4L, 1, 90, from.plusSeconds(3)));
        when(telemetry.findAllForOperationalReport(List.of(4L), toExclusive))
                .thenReturn(samples);

        var result = service.operations(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 21), null, null);

        assertThat(result.overspeedEventCount()).isEqualTo(1);
    }

    private TripEntity trip(Long id, RouteEntity route, TripStatus status, String started, String ended, String departure) {
        TripEntity trip = org.mockito.Mockito.mock(TripEntity.class);
        when(trip.getId()).thenReturn(id);
        when(trip.getRoute()).thenReturn(route);
        when(trip.getStatus()).thenReturn(status);
        when(trip.getSchedule()).thenReturn(org.mockito.Mockito.mock(TripScheduleEntity.class));
        when(trip.getStartedAt()).thenReturn(Instant.parse(started));
        when(trip.getEndedAt()).thenReturn(ended == null ? null : Instant.parse(ended));
        when(trip.getScheduledDepartureAt()).thenReturn(Instant.parse(departure));
        when(trip.getStops()).thenReturn(List.of());
        return trip;
    }

    private TelemetrySampleEntity sample(Long tripId, int attempt, double speed) {
        return sample(tripId, attempt, speed, Instant.parse("2026-09-21T08:00:00Z"));
    }

    private TelemetrySampleEntity sample(Long tripId, int attempt, double speed, Instant recordedAt) {
        TelemetrySampleEntity sample = org.mockito.Mockito.mock(TelemetrySampleEntity.class);
        when(sample.getTripId()).thenReturn(tripId);
        when(sample.getAttemptNumber()).thenReturn(attempt);
        when(sample.getSpeedKmh()).thenReturn(speed);
        when(sample.getRecordedAt()).thenReturn(recordedAt);
        return sample;
    }
}
