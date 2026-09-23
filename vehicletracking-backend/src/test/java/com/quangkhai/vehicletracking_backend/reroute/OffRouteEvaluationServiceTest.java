package com.quangkhai.vehicletracking_backend.reroute;

import com.quangkhai.vehicletracking_backend.checkin.repository.TripStopVisitRepository;
import com.quangkhai.vehicletracking_backend.config.OffRouteProperties;
import com.quangkhai.vehicletracking_backend.reroute.entity.TripOffRouteAlertStateEntity;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripNotificationRepository;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripOffRouteAlertStateRepository;
import com.quangkhai.vehicletracking_backend.reroute.service.OffRouteEvaluationService;
import com.quangkhai.vehicletracking_backend.reroute.service.TripRouteGeometryService;
import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
import com.quangkhai.vehicletracking_backend.simulation.SimulationFixtures;
import com.quangkhai.vehicletracking_backend.telemetry.entity.TelemetrySampleEntity;
import com.quangkhai.vehicletracking_backend.telemetry.entity.TelemetrySource;
import com.quangkhai.vehicletracking_backend.telemetry.entity.VehiclePositionEntity;
import com.quangkhai.vehicletracking_backend.telemetry.repository.VehiclePositionRepository;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OffRouteEvaluationServiceTest {
    private static final Instant START = Instant.parse("2026-01-01T08:00:00Z");

    private final TripRepository trips = mock(TripRepository.class);
    private final TripStopVisitRepository visits = mock(TripStopVisitRepository.class);
    private final VehiclePositionRepository positions = mock(VehiclePositionRepository.class);
    private final TripRouteGeometryService geometry = mock(TripRouteGeometryService.class);
    private final TripOffRouteAlertStateRepository states = mock(TripOffRouteAlertStateRepository.class);
    private final TripNotificationRepository notifications = mock(TripNotificationRepository.class);
    private final OffRouteProperties properties = properties();
    private final TripEntity trip = mock(TripEntity.class);
    private final VehicleEntity vehicle = mock(VehicleEntity.class);

    @Test
    void emitsOneNotificationAfterThreeSamplesAndThirtySecondsOutsideTheRoute() {
        var state = new TripOffRouteAlertStateEntity(trip, 1, START);
        var first = sample(START.plusSeconds(1), 10.7800, 106.7100);
        var second = sample(START.plusSeconds(15), 10.7800, 106.7100);
        var third = sample(START.plusSeconds(31), 10.7800, 106.7100);
        stubTripAndRoute();
        when(states.findLockedByTripId(1L)).thenReturn(Optional.of(state));
        when(positions.findById(2L))
                .thenReturn(Optional.of(new VehiclePositionEntity(2L, first)))
                .thenReturn(Optional.of(new VehiclePositionEntity(2L, second)))
                .thenReturn(Optional.of(new VehiclePositionEntity(2L, third)));
        var service = service();

        service.evaluateCurrent(1L);
        service.evaluateCurrent(1L);
        service.evaluateCurrent(1L);
        service.evaluateCurrent(1L);

        verify(notifications, times(1)).save(any());
        assertThat(state.isActive()).isTrue();
        assertThat(state.getEpisode()).isEqualTo(1);
        assertThat(state.getConsecutiveBreachCount()).isEqualTo(3);
    }

    @Test
    void doesNotAlertWhenPositionIsInsideEffectiveAccuracyThreshold() {
        var state = new TripOffRouteAlertStateEntity(trip, 1, START);
        var sample = sample(START.plusSeconds(30), 10.7700, 106.7000);
        sample = sampleWithAccuracy(sample, 250);
        stubTripAndRoute();
        when(states.findLockedByTripId(1L)).thenReturn(Optional.of(state));
        when(positions.findById(2L)).thenReturn(Optional.of(new VehiclePositionEntity(2L, sample)));

        service().evaluateCurrent(1L);

        verify(notifications, never()).save(any());
        assertThat(state.isActive()).isFalse();
        assertThat(state.getConsecutiveBreachCount()).isZero();
    }

    private OffRouteEvaluationService service() {
        return new OffRouteEvaluationService(trips, visits, positions, geometry, states, notifications,
                Clock.fixed(START.plusSeconds(40), ZoneOffset.UTC), properties);
    }

    private void stubTripAndRoute() {
        when(trips.findLockedById(1L)).thenReturn(Optional.of(trip));
        when(trip.getId()).thenReturn(1L);
        when(trip.getVehicle()).thenReturn(vehicle);
        when(trip.getAttemptNumber()).thenReturn(1);
        when(trip.getStatus()).thenReturn(TripStatus.IN_PROGRESS);
        when(vehicle.getId()).thenReturn(2L);
        when(visits.findAllByTripIdOrderByStopSequenceAsc(1L)).thenReturn(List.of());
        when(geometry.routeForTracking(trip)).thenReturn(route());
    }

    private RouteDetailResponse route() {
        var section = new RouteDetailResponse.RouteSectionResponse(1, 2,
                SimulationFixtures.encode(new double[][]{{10.7700, 106.7000}, {10.7710, 106.7010}}), 150, 30, 30);
        var stops = List.of(
                new RouteDetailResponse.RouteStopResponse(1, "START", 1L, "A", BigDecimal.valueOf(10.77), BigDecimal.valueOf(106.70), 0, 0, 0, 0, 0),
                new RouteDetailResponse.RouteStopResponse(2, "END", 2L, "B", BigDecimal.valueOf(10.771), BigDecimal.valueOf(106.701), 0, 150, 30, 30, 30));
        return new RouteDetailResponse(1L, "A-B", null, null, 150, 30, 30, 0, 30,
                START, START, START, stops, List.of(section));
    }

    private TelemetrySampleEntity sample(Instant recordedAt, double latitude, double longitude) {
        var sample = mock(TelemetrySampleEntity.class);
        when(sample.getSource()).thenReturn(TelemetrySource.GPS);
        when(sample.getTripId()).thenReturn(1L);
        when(sample.getAttemptNumber()).thenReturn(1);
        when(sample.getRecordedAt()).thenReturn(recordedAt);
        when(sample.getLatitude()).thenReturn(latitude);
        when(sample.getLongitude()).thenReturn(longitude);
        when(sample.getAccuracyMeters()).thenReturn(5d);
        return sample;
    }

    private TelemetrySampleEntity sampleWithAccuracy(TelemetrySampleEntity sample, double accuracy) {
        when(sample.getAccuracyMeters()).thenReturn(accuracy);
        return sample;
    }

    private OffRouteProperties properties() {
        var result = new OffRouteProperties();
        result.setThresholdMeters(150);
        result.setGracePeriodSeconds(30);
        result.setConsecutiveSamples(3);
        return result;
    }
}
