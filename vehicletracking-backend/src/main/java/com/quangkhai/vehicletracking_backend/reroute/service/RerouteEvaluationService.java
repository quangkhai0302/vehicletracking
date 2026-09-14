package com.quangkhai.vehicletracking_backend.reroute.service;

import com.quangkhai.vehicletracking_backend.checkin.repository.TripStopVisitRepository;
import com.quangkhai.vehicletracking_backend.config.RerouteProperties;
import com.quangkhai.vehicletracking_backend.route.provider.*;
import com.quangkhai.vehicletracking_backend.reroute.entity.*;
import com.quangkhai.vehicletracking_backend.reroute.repository.*;
import com.quangkhai.vehicletracking_backend.telemetry.repository.VehiclePositionRepository;
import com.quangkhai.vehicletracking_backend.traffic.eta.TripEtaResponse;
import com.quangkhai.vehicletracking_backend.traffic.eta.TrafficEtaService;
import com.quangkhai.vehicletracking_backend.trip.entity.*;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RerouteEvaluationService {
    private final TripRepository trips;
    private final TripStopVisitRepository visits;
    private final VehiclePositionRepository positions;
    private final RoutingProvider routing;
    private final TripRouteRevisionRepository revisions;
    private final TripTrafficAlertStateRepository states;
    private final TripNotificationRepository notifications;
    private final TrafficEtaService trafficEta;
    private final Clock operationsClock;
    private final RerouteProperties properties;

    /** Evaluates one trip. It is safe to call for every scheduler tick and ETA read. */
    // Evaluation is deliberately isolated from telemetry/simulator writes.
    // A provider or revision persistence failure must not mark the caller's
    // position transaction rollback-only. This also lets the after-commit
    // trigger start a fresh transaction while Spring is cleaning up the
    // telemetry transaction resources.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void evaluate(long tripId, TripEtaResponse currentEta) {
        TripEntity trip = trips.findLockedById(tripId).orElse(null);
        if (trip == null || trip.getStatus() != TripStatus.IN_PROGRESS || currentEta == null) return;
        if (currentEta.trafficFetchedAt() == null
                || (currentEta.source() != com.quangkhai.vehicletracking_backend.traffic.TrafficSource.HERE_LIVE
                && currentEta.source() != com.quangkhai.vehicletracking_backend.traffic.TrafficSource.HERE_LAST_KNOWN)) return;
        Instant now = operationsClock.instant();
        String fingerprint = fingerprint(currentEta);
        TripTrafficAlertStateEntity state = states.findById(tripId).orElseGet(() -> states.save(new TripTrafficAlertStateEntity(trip, now)));
        ReroutePolicy.Decision decision = ReroutePolicy.observe(currentEta, state.getLastTrafficFetchedAt(),
                state.getBreachFingerprint(), state.getBreachCount(), state.getLastTriggeredFingerprint(),
                state.getLastTriggerAt(), fingerprint, now, properties);
        if (!decision.breach()) {
            state.clear(currentEta.trafficFetchedAt(), now);
            states.save(state);
            return;
        }
        state.observe(currentEta.trafficFetchedAt(), fingerprint, decision.consecutiveCount(), now);
        states.save(state);
        if (!decision.trigger()) return;

        boolean closure = currentEta.status() == com.quangkhai.vehicletracking_backend.traffic.TrafficStatus.BLOCKED
                || currentEta.affectedSegments().stream().anyMatch(s -> "INCIDENT".equals(s.kind())
                && (s.traversability() == null || s.traversability().toLowerCase(Locale.ROOT).contains("closure")));
        String dedupeBase = tripId + ":" + fingerprint;
        try {
            TripRouteRevisionEntity revision = buildRevision(trip, currentEta, closure, now);
            if (revision == null) {
                createUnavailable(trip, currentEta, dedupeBase, now);
            } else {
                revisions.saveAndFlush(revision);
                String key = "REROUTE_CREATED:" + dedupeBase;
                if (notifications.findByDedupeKey(key).isEmpty()) {
                    notifications.save(new TripNotificationEntity(trip, revision, NotificationType.REROUTE_CREATED,
                            revision.getSeverity(), "Đã tạo tuyến thay thế", revision.getReasonDetail(), revision.getTriggerIncidentId(),
                            affectedStops(currentEta), currentEta.baselineRemainingSeconds(), revision.getRevisedRemainingSeconds(), key, now));
                }
            }
        } catch (RuntimeException ex) {
            createUnavailable(trip, currentEta, dedupeBase, now);
        }
        state.markTriggered(fingerprint, now);
        states.save(state);
    }

    /**
     * Computes the traffic snapshot and evaluates it in the same isolated
     * transaction. Callers that just accepted telemetry should use this
     * method so the ETA read cannot accidentally join the already-committed
     * telemetry transaction during an after-commit callback.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void evaluateCurrent(long tripId) {
        evaluate(tripId, trafficEta.calculate(tripId));
    }

    private TripRouteRevisionEntity buildRevision(TripEntity trip, TripEtaResponse currentEta, boolean closure, Instant now) {
        var position = positions.findById(trip.getVehicle().getId()).map(p -> p.getSample()).orElse(null);
        if (position == null || !trip.getId().equals(position.getTripId())) return null;
        Set<Integer> checked = visits.findAllByTripIdOrderByStopSequenceAsc(trip.getId()).stream()
                .map(v -> v.getStopSequence()).collect(Collectors.toSet());
        List<TripStopEntity> remaining = trip.getStops().stream().filter(s -> !checked.contains(s.getSequenceNumber()))
                .sorted(Comparator.comparing(TripStopEntity::getSequenceNumber)).toList();
        if (remaining.isEmpty()) return null;
        List<RoutingWaypoint> waypoints = new ArrayList<>();
        waypoints.add(new RoutingWaypoint(null, "Current vehicle position", BigDecimal.valueOf(position.getLatitude()),
                BigDecimal.valueOf(position.getLongitude()), 1, 0));
        for (TripStopEntity stop : remaining) {
            waypoints.add(new RoutingWaypoint(stop.getStationId(), stop.getStationName(), stop.getLatitude(), stop.getLongitude(),
                    stop.getSequenceNumber(), stop.getDwellDurationSeconds()));
        }
        CalculatedRoute route = routing.calculate(waypoints);
        long revisedSeconds = route.sections().stream().mapToLong(CalculatedSection::travelDurationSeconds).sum()
                + remaining.stream().mapToLong(TripStopEntity::getDwellDurationSeconds).sum();
        if (!closure && revisedSeconds >= currentEta.totalRemainingSeconds()) return null;
        String detail = closure ? "Phát hiện đường bị đóng/chặn từ HERE Traffic" : "Độ trễ giao thông vượt ngưỡng, đã chọn tuyến nhanh hơn";
        NotificationSeverity severity = closure ? NotificationSeverity.CRITICAL : NotificationSeverity.MAJOR;
        TripRouteRevisionEntity revision = new TripRouteRevisionEntity(trip, trip.getRoute(), revisions.countByTripId(trip.getId()) + 1,
                closure ? RerouteReasonCode.ROAD_CLOSURE : RerouteReasonCode.TRAFFIC_DELAY, detail,
                incidentId(currentEta), severity, currentEta.baselineRemainingSeconds(), revisedSeconds, now);
        revisions.findTopByTripIdAndStatusOrderByRevisionNumberDesc(trip.getId(), RouteRevisionStatus.ACTIVE)
                .ifPresent(active -> {
                    active.supersede(now);
                    // The database enforces one ACTIVE revision per trip.
                    // Flush the supersede before inserting the replacement so
                    // the partial unique index is never violated by flush order.
                    revisions.saveAndFlush(active);
                });
        Instant cursor = now;
        Map<Integer, TripStopEntity> byOriginal = remaining.stream().collect(Collectors.toMap(TripStopEntity::getSequenceNumber, s -> s));
        for (int local = 2; local <= waypoints.size(); local++) {
            final int destinationLocal = local;
            int original = waypoints.get(local - 1).sequenceNumber();
            TripStopEntity stop = byOriginal.get(original);
            if (stop == null) continue;
            List<CalculatedSection> legSections = route.sections().stream()
                    .filter(s -> s.destinationStopSequence() == destinationLocal)
                    .toList();
            if (legSections.isEmpty()) continue;
            for (CalculatedSection section : legSections) {
                cursor = cursor.plusSeconds(Math.max(0, section.travelDurationSeconds()));
                revision.addSection(new TripRouteRevisionSectionEntity(section.sectionSequence(), original, section.encodedPolyline(),
                        section.distanceMeters(), section.travelDurationSeconds(), section.baseTravelDurationSeconds()));
            }
            Instant arrival = cursor;
            Instant departure = arrival.plusSeconds(stop.getDwellDurationSeconds());
            revision.addStop(new TripRouteRevisionStopEntity(original, local - 1, stop.getStationId(), stop.getStationName(),
                    stop.getLatitude(), stop.getLongitude(), stop.getDwellDurationSeconds(), stop.getPlannedArrivalAt(),
                    stop.getPlannedDepartureAt(), arrival, departure));
            cursor = departure;
        }
        if (revision.getStops().size() != remaining.size()) {
            throw new IllegalStateException("Routing provider omitted a remaining stop");
        }
        return revision;
    }

    private void createUnavailable(TripEntity trip, TripEtaResponse currentEta, String base, Instant now) {
        String key = "REROUTE_UNAVAILABLE:" + base;
        if (notifications.findByDedupeKey(key).isPresent()) return;
        notifications.save(new TripNotificationEntity(trip, null, NotificationType.REROUTE_UNAVAILABLE,
                currentEta.status() == com.quangkhai.vehicletracking_backend.traffic.TrafficStatus.BLOCKED ? NotificationSeverity.CRITICAL : NotificationSeverity.MAJOR,
                "Không thể tạo tuyến thay thế", "HERE Routing không trả về tuyến khả dụng hoặc tuyến mới không cải thiện ETA.",
                incidentId(currentEta), affectedStops(currentEta), currentEta.baselineRemainingSeconds(), null, key, now));
    }

    private String fingerprint(TripEtaResponse eta) {
        String values = eta.affectedSegments().stream().map(s -> s.kind() + ":" + s.id() + ":" + s.destinationStopSequence())
                .sorted().collect(Collectors.joining("|"));
        return (eta.status() == com.quangkhai.vehicletracking_backend.traffic.TrafficStatus.BLOCKED ? "BLOCKED:" : "DELAY:")
                + (values.isBlank() ? "route" : values);
    }
    private String affectedStops(TripEtaResponse eta) { return eta.affectedSegments().stream().map(s -> Integer.toString(s.destinationStopSequence())).distinct().sorted().collect(Collectors.joining(",")); }
    private String incidentId(TripEtaResponse eta) { return eta.affectedSegments().stream().filter(s -> "INCIDENT".equals(s.kind())).map(TripEtaResponse.AffectedSegment::id).findFirst().orElse(null); }
}
