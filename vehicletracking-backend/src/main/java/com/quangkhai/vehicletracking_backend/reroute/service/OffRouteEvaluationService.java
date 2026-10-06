package com.quangkhai.vehicletracking_backend.reroute.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.quangkhai.vehicletracking_backend.checkin.repository.TripStopVisitRepository;
import com.quangkhai.vehicletracking_backend.config.OffRouteProperties;
import com.quangkhai.vehicletracking_backend.reroute.entity.NotificationSeverity;
import com.quangkhai.vehicletracking_backend.reroute.entity.NotificationType;
import com.quangkhai.vehicletracking_backend.reroute.entity.TripNotificationEntity;
import com.quangkhai.vehicletracking_backend.reroute.entity.TripOffRouteAlertStateEntity;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripNotificationRepository;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripOffRouteAlertStateRepository;
import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
import com.quangkhai.vehicletracking_backend.telemetry.entity.TelemetrySource;
import com.quangkhai.vehicletracking_backend.telemetry.entity.VehiclePositionEntity;
import com.quangkhai.vehicletracking_backend.telemetry.repository.VehiclePositionRepository;
import com.quangkhai.vehicletracking_backend.traffic.matching.RoutePositionMatcher;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Evaluates GPS distance from the active trip route without affecting telemetry persistence. */
@Service
@RequiredArgsConstructor
@Slf4j
public class OffRouteEvaluationService {
    private final TripRepository trips;
    private final TripStopVisitRepository visits;
    private final VehiclePositionRepository positions;
    private final TripRouteGeometryService geometry;
    private final TripOffRouteAlertStateRepository states;
    private final TripNotificationRepository notifications;
    private final Clock operationsClock;
    private final OffRouteProperties properties;
    private final com.quangkhai.vehicletracking_backend.simulation.repository.SimulationRepository simulations;
    private final RoutePositionMatcher matcher = new RoutePositionMatcher();

    @Transactional
    public void clearScenarioEpisode(long tripId, Instant now) {
        states.findLockedByTripId(tripId).ifPresent(state -> state.clear(null,now));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void evaluateCurrent(long tripId) {
        if (!properties.isEnabled()) return;
        TripEntity trip = trips.findLockedById(tripId).orElse(null);
        if (trip == null || trip.getStatus() != TripStatus.IN_PROGRESS) return;

        VehiclePositionEntity position = positions.findById(trip.getVehicle().getId()).orElse(null);
        if (position == null || position.getSample() == null) return;
        var sample = position.getSample();
        if (!trip.getId().equals(sample.getTripId())
                || sample.getAttemptNumber() != trip.getAttemptNumber()) return;
        Instant measuredAt=sample.getRecordedAt();
        if(sample.getSource()==TelemetrySource.SIMULATOR) {
            var run=simulations.findByTripId(tripId).orElse(null);
            if(run==null || run.getVirtualElapsedSeconds()==null || run.getStatus()!=com.quangkhai.vehicletracking_backend.simulation.entity.SimulationStatus.RUNNING) return;
            measuredAt=Instant.EPOCH.plusMillis((long)(run.getVirtualElapsedSeconds()*1000));
        }

        RouteDetailResponse route;
        try {
            route = geometry.routeForTracking(trip);
        } catch (RuntimeException ex) {
            log.warn("Không thể tải hình học tuyến để đánh giá lệch tuyến cho chuyến {}", tripId, ex);
            return;
        }
        int nextStop = nextStop(trip, route);
        int firstRemainingSection = firstRemainingSection(route, nextStop);
        if (firstRemainingSection >= route.sections().size()) return;
        var projection = matcher.project(route.sections(), sample.getLatitude(), sample.getLongitude(),
                firstRemainingSection, Double.MAX_VALUE).orElse(null);
        if (projection == null) return;

        Instant now = operationsClock.instant();
        TripOffRouteAlertStateEntity state = states.findLockedByTripId(tripId)
                .orElseGet(() -> states.save(new TripOffRouteAlertStateEntity(trip, trip.getAttemptNumber(), now)));
        if (state.getAttemptNumber() != trip.getAttemptNumber()) state.resetForAttempt(trip.getAttemptNumber(), now);
        if (state.getLastRecordedAt() != null && !measuredAt.isAfter(state.getLastRecordedAt())) return;

        double effectiveThreshold = Math.max(properties.getThresholdMeters(), sample.getAccuracyMeters());
        if (projection.distanceToRouteMeters() <= effectiveThreshold) {
            state.clear(measuredAt, now);
            states.save(state);
            return;
        }

        state.observeBreach(projection.distanceToRouteMeters(), measuredAt, now);
        long durationSeconds = state.getBreachStartedAt() == null ? 0
                : Math.max(0, Duration.between(state.getBreachStartedAt(), measuredAt).getSeconds());
        boolean ready = state.getConsecutiveBreachCount() >= properties.getConsecutiveSamples()
                && durationSeconds >= properties.getGracePeriodSeconds();
        if (ready && !state.isActive()) {
            state.markActive(now);
            String reason = String.format(java.util.Locale.ROOT,
                    "Xe cách tuyến %.0f m, vượt ngưỡng %.0f m trong %d giây.",
                    projection.distanceToRouteMeters(), effectiveThreshold, durationSeconds);
            var notification=new TripNotificationEntity(trip, null, NotificationType.OFF_ROUTE_DETECTED,
                    NotificationSeverity.MAJOR, "Xe lệch tuyến", reason, null,
                    nextStop < 0 ? "" : Integer.toString(nextStop), null, null,
                    projection.distanceToRouteMeters(), effectiveThreshold, durationSeconds,
                    "OFF_ROUTE_DETECTED:" + tripId + ":" + trip.getAttemptNumber() + ":" + state.getEpisode(), now);
            if(sample.getSource()==TelemetrySource.SIMULATOR) notification.attributeSimulation(trip.getAttemptNumber());
            notifications.save(notification);
        }
        states.save(state);
    }

    private int nextStop(TripEntity trip, RouteDetailResponse route) {
        var checked = new HashSet<Integer>();
        visits.findAllByTripIdAndAttemptNumberOrderByStopSequenceAsc(trip.getId(),trip.getAttemptNumber()).forEach(item -> checked.add(item.getStopSequence()));
        return route.stops().stream().filter(stop -> !checked.contains(stop.sequenceNumber()))
                .mapToInt(stop -> stop.sequenceNumber()).findFirst().orElse(-1);
    }

    private int firstRemainingSection(RouteDetailResponse route, int nextStop) {
        if (nextStop < 0) return route.sections().size();
        for (int index = 0; index < route.sections().size(); index++) {
            if (route.sections().get(index).destinationStopSequence() >= nextStop) return index;
        }
        return route.sections().size();
    }
}
