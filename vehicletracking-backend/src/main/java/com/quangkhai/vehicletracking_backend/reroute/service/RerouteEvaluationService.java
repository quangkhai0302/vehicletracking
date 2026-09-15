package com.quangkhai.vehicletracking_backend.reroute.service;

import com.quangkhai.vehicletracking_backend.checkin.repository.TripStopVisitRepository;
import com.quangkhai.vehicletracking_backend.config.RerouteProperties;
import com.quangkhai.vehicletracking_backend.route.provider.*;
import com.quangkhai.vehicletracking_backend.reroute.entity.*;
import com.quangkhai.vehicletracking_backend.reroute.repository.*;
import com.quangkhai.vehicletracking_backend.telemetry.repository.VehiclePositionRepository;
import com.quangkhai.vehicletracking_backend.traffic.eta.TripEtaResponse;
import com.quangkhai.vehicletracking_backend.traffic.eta.TrafficEtaService;
import com.quangkhai.vehicletracking_backend.traffic.matching.RoutePositionMatcher;
import com.quangkhai.vehicletracking_backend.trip.entity.*;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final RoutingProviderRegistry routing;
    private final TripRouteRevisionRepository revisions;
    private final TripTrafficAlertStateRepository states;
    private final TripNotificationRepository notifications;
    private final TrafficEtaService trafficEta;
    private final TripRouteGeometryService geometry;
    private final Clock operationsClock;
    private final RerouteProperties properties;
    private final PlatformTransactionManager transactionManager;

    /**
     * Evaluates one trip using snapshot -> provider -> compare-and-write phases.
     * The potentially slow provider call explicitly suspends any caller transaction.
     */
    public void evaluate(long tripId, TripEtaResponse currentEta) {
        EvaluationPlan plan = writeTransaction()
                .execute(status -> prepare(tripId, currentEta));
        if (plan == null) return;

        CalculatedRoute candidate = withoutTransaction().execute(status -> calculate(plan));
        writeTransaction().executeWithoutResult(status -> finish(plan, candidate));
    }

    private CalculatedRoute calculate(EvaluationPlan plan) {
        try {
            return routing.calculate(plan.provider(),
                    new RoutingRequest(plan.waypoints(), plan.transportMode(), plan.now(), true));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private TransactionTemplate writeTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private TransactionTemplate withoutTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        return template;
    }

    private EvaluationPlan prepare(long tripId, TripEtaResponse currentEta) {
        TripEntity trip = trips.findLockedById(tripId).orElse(null);
        if (trip == null || trip.getStatus() != TripStatus.IN_PROGRESS || currentEta == null) return null;
        if (currentEta.trafficFetchedAt() == null
                || (currentEta.source() != com.quangkhai.vehicletracking_backend.traffic.TrafficSource.HERE_LIVE
                && currentEta.source() != com.quangkhai.vehicletracking_backend.traffic.TrafficSource.HERE_LAST_KNOWN
                && currentEta.source() != com.quangkhai.vehicletracking_backend.traffic.TrafficSource.GOOGLE_LIVE
                && currentEta.source() != com.quangkhai.vehicletracking_backend.traffic.TrafficSource.GOOGLE_LAST_KNOWN)) return null;
        Instant now = operationsClock.instant();
        String fingerprint = fingerprint(currentEta);
        TripTrafficAlertStateEntity state = states.findById(tripId).orElseGet(() -> states.save(new TripTrafficAlertStateEntity(trip, now)));
        ReroutePolicy.Decision decision = ReroutePolicy.observe(currentEta, state.getLastTrafficFetchedAt(),
                state.getBreachFingerprint(), state.getBreachCount(), state.getLastTriggeredFingerprint(),
                state.getLastTriggerAt(), fingerprint, now, properties);
        // Scheduler/ETA reads reuse a cached HERE observation. Do not erase the
        // consecutive breach count between two genuine provider refreshes.
        if ("TRAFFIC_NOT_REFRESHED".equals(decision.reason())) return null;
        if (!decision.breach()) {
            state.clear(currentEta.trafficFetchedAt(), now);
            states.save(state);
            return null;
        }
        state.observe(currentEta.trafficFetchedAt(), fingerprint, decision.consecutiveCount(), now);
        states.save(state);
        if (!decision.trigger()) return null;

        boolean closure = currentEta.status() == com.quangkhai.vehicletracking_backend.traffic.TrafficStatus.BLOCKED
                || currentEta.affectedSegments().stream().anyMatch(s -> "INCIDENT".equals(s.kind())
                && (s.traversability() == null || s.traversability().toLowerCase(Locale.ROOT).contains("closure")));
        String dedupeBase = tripId + ":" + fingerprint;
        EvaluationPlan plan = prepareCalculation(trip, currentEta, closure, now, dedupeBase);
        // Reserve this observation before releasing the lock so concurrent readers cannot launch duplicate HTTP calls.
        state.markTriggered(fingerprint, now);
        states.save(state);
        if (plan == null) {
            createUnavailable(trip, currentEta, dedupeBase, now);
            return null;
        }
        return plan;
    }

    /** Computes a current ETA first, then evaluates it using the isolated phases above. */
    public void evaluateCurrent(long tripId) {
        evaluate(tripId, trafficEta.calculate(tripId));
    }

    private EvaluationPlan prepareCalculation(TripEntity trip, TripEtaResponse currentEta, boolean closure,
                                              Instant now, String dedupeBase) {
        var position = positions.findById(trip.getVehicle().getId()).map(p -> p.getSample()).orElse(null);
        if (position == null || !trip.getId().equals(position.getTripId()) || position.getAttemptNumber()!=trip.getAttemptNumber()) return null;
        Set<Integer> checked = visits.findAllByTripIdOrderByStopSequenceAsc(trip.getId()).stream()
                .map(v -> v.getStopSequence()).collect(Collectors.toSet());
        Integer simulationNext=null;
        if (position.getSource()==com.quangkhai.vehicletracking_backend.telemetry.entity.TelemetrySource.SIMULATOR
                && position.getSimulatedAt()!=null) {
            double elapsed=Duration.between(trip.getScheduledDepartureAt(),position.getSimulatedAt()).toNanos()/1_000_000_000d;
            var frame=geometry.resolve(trip).motion().at(elapsed);
            if (frame.dwelling() || frame.finished()) return null;
            // Check-in occurs at the geofence edge, before the vehicle reaches
            // the actual stop. Do not remove that still-upcoming stop from the path.
            simulationNext=frame.nextStopSequence();
        }
        final Integer next=simulationNext;
        List<TripStopEntity> remaining = trip.getStops().stream().filter(s -> next==null
                ? !checked.contains(s.getSequenceNumber()) : s.getSequenceNumber()>=next)
                .sorted(Comparator.comparing(TripStopEntity::getSequenceNumber)).toList();
        if (remaining.isEmpty()) return null;
        List<RoutingWaypoint> waypoints = new ArrayList<>();
        waypoints.add(new RoutingWaypoint(null, "Current vehicle position", BigDecimal.valueOf(position.getLatitude()),
                BigDecimal.valueOf(position.getLongitude()), 1, 0));
        var activeRoute = geometry.route(trip);
        int firstRemainingSection = 0;
        int firstRemainingStop = remaining.getFirst().getSequenceNumber();
        while (firstRemainingSection < activeRoute.sections().size()
                && activeRoute.sections().get(firstRemainingSection).destinationStopSequence() < firstRemainingStop) {
            firstRemainingSection++;
        }
        var positionMatcher = new RoutePositionMatcher();
        var currentProjection = positionMatcher.project(activeRoute.sections(), position.getLatitude(), position.getLongitude(),
                firstRemainingSection, 100).orElse(null);
        for (TripStopEntity stop : remaining) {
            for (var shape : activeRoute.shapingPoints()) {
                if (shape.destinationStopSequence() != stop.getSequenceNumber()
                        || !isAhead(shape, stop.getSequenceNumber(), firstRemainingStop, activeRoute,
                        firstRemainingSection, currentProjection, positionMatcher)) continue;
                waypoints.add(new RoutingWaypoint(null, "Điểm dẫn đường", shape.latitude(), shape.longitude(),
                        stop.getSequenceNumber(), 0));
            }
            waypoints.add(new RoutingWaypoint(stop.getStationId(), stop.getStationName(), stop.getLatitude(), stop.getLongitude(),
                    stop.getSequenceNumber(), stop.getDwellDurationSeconds()));
        }
        List<StopSnapshot> stopSnapshots = remaining.stream().map(stop -> new StopSnapshot(
                stop.getSequenceNumber(), stop.getStationId(), stop.getStationName(), stop.getLatitude(),
                stop.getLongitude(), stop.getDwellDurationSeconds(), stop.getPlannedArrivalAt(),
                stop.getPlannedDepartureAt())).toList();
        return new EvaluationPlan(trip.getId(), trip.getRoute().getId(), trip.getAttemptNumber(),
                position.getId(), geometry.appliedRevisionId(trip), trip.getRoute().getRoutingProvider(),
                trip.getRoute().getTransportMode(), waypoints, stopSnapshots, currentEta, closure, now, dedupeBase);
    }

    private void finish(EvaluationPlan plan, CalculatedRoute route) {
        TripEntity trip = trips.findLockedById(plan.tripId()).orElse(null);
        if (trip == null || trip.getStatus() != TripStatus.IN_PROGRESS
                || trip.getAttemptNumber() != plan.attemptNumber()
                || !trip.getRoute().getId().equals(plan.routeId())
                || !Objects.equals(geometry.appliedRevisionId(trip), plan.appliedRevisionId())) return;
        var latest = positions.findById(trip.getVehicle().getId()).map(p -> p.getSample()).orElse(null);
        if (latest == null || !Objects.equals(latest.getId(), plan.positionSampleId())) return;

        if (route == null || plan.closure()
                && RerouteCandidateValidator.intersectsTriggeringClosure(route, plan.currentEta(), 30)) {
            createUnavailable(trip, plan.currentEta(), plan.dedupeBase(), plan.now());
            return;
        }
        try {
            TripRouteRevisionEntity revision = persistRevision(trip, plan, route);
            if (revision == null) {
                createUnavailable(trip, plan.currentEta(), plan.dedupeBase(), plan.now());
                return;
            }
            revisions.saveAndFlush(revision);
            String key = "REROUTE_CREATED:" + plan.dedupeBase();
            if (notifications.findByDedupeKey(key).isEmpty()) {
                notifications.save(new TripNotificationEntity(trip, revision, NotificationType.REROUTE_CREATED,
                        revision.getSeverity(), "Đã tạo tuyến thay thế", revision.getReasonDetail(), revision.getTriggerIncidentId(),
                        affectedStops(plan.currentEta()), plan.currentEta().baselineRemainingSeconds(),
                        revision.getRevisedRemainingSeconds(), key, plan.now()));
            }
        } catch (RuntimeException ex) {
            createUnavailable(trip, plan.currentEta(), plan.dedupeBase(), plan.now());
        }
    }

    private TripRouteRevisionEntity persistRevision(TripEntity trip, EvaluationPlan plan, CalculatedRoute route) {
        TripEtaResponse currentEta = plan.currentEta();
        boolean closure = plan.closure();
        Instant now = plan.now();
        List<StopSnapshot> remaining = plan.remainingStops();
        List<RoutingWaypoint> waypoints = plan.waypoints();
        long revisedSeconds = route.sections().stream().mapToLong(CalculatedSection::travelDurationSeconds).sum()
                + remaining.stream().mapToLong(StopSnapshot::dwellDurationSeconds).sum();
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
        Map<Integer, StopSnapshot> byOriginal = remaining.stream().collect(Collectors.toMap(StopSnapshot::sequenceNumber, s -> s));
        int previousRequiredLocal = 1;
        int revisionStopSequence = 0;
        for (int local = 2; local <= waypoints.size(); local++) {
            RoutingWaypoint destinationWaypoint = waypoints.get(local - 1);
            if (destinationWaypoint.stationId() == null) continue;
            final int destinationLocal = local;
            final int sectionStartExclusive = previousRequiredLocal;
            int original = destinationWaypoint.sequenceNumber();
            StopSnapshot stop = byOriginal.get(original);
            if (stop == null) continue;
            List<CalculatedSection> legSections = route.sections().stream()
                    .filter(s -> s.destinationStopSequence() > sectionStartExclusive
                            && s.destinationStopSequence() <= destinationLocal)
                    .toList();
            if (legSections.isEmpty()) continue;
            for (CalculatedSection section : legSections) {
                cursor = cursor.plusSeconds(Math.max(0, section.travelDurationSeconds()));
                revision.addSection(new TripRouteRevisionSectionEntity(section.sectionSequence(), original, section.encodedPolyline(),
                        section.polylineEncoding(),
                        section.distanceMeters(), section.travelDurationSeconds(), section.baseTravelDurationSeconds(),
                        section.trafficIntervals()));
            }
            Instant arrival = cursor;
            Instant departure = arrival.plusSeconds(stop.dwellDurationSeconds());
            revision.addStop(new TripRouteRevisionStopEntity(original, ++revisionStopSequence, stop.stationId(), stop.stationName(),
                    stop.latitude(), stop.longitude(), stop.dwellDurationSeconds(), stop.plannedArrivalAt(),
                    stop.plannedDepartureAt(), arrival, departure));
            cursor = departure;
            previousRequiredLocal = local;
        }
        if (revision.getStops().size() != remaining.size()) {
            throw new IllegalStateException("Routing provider omitted a remaining stop");
        }
        return revision;
    }

    private record StopSnapshot(int sequenceNumber, Long stationId, String stationName, BigDecimal latitude,
                                BigDecimal longitude, int dwellDurationSeconds, Instant plannedArrivalAt,
                                Instant plannedDepartureAt) {}

    private record EvaluationPlan(long tripId, long routeId, int attemptNumber, Long positionSampleId,
                                  Long appliedRevisionId, com.quangkhai.vehicletracking_backend.route.entity.RoutingProviderName provider,
                                  com.quangkhai.vehicletracking_backend.route.entity.RouteTransportMode transportMode,
                                  List<RoutingWaypoint> waypoints, List<StopSnapshot> remainingStops,
                                  TripEtaResponse currentEta, boolean closure, Instant now, String dedupeBase) {}

    private boolean isAhead(com.quangkhai.vehicletracking_backend.route.dto.RouteShapeRequest.ShapePoint shape,
                            int destinationStop, int firstRemainingStop,
                            com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse activeRoute,
                            int firstRemainingSection, RoutePositionMatcher.Projection currentProjection,
                            RoutePositionMatcher matcher) {
        if (destinationStop > firstRemainingStop) return true;
        if (currentProjection == null) return false;
        var shapeProjection = matcher.project(activeRoute.sections(), shape.latitude().doubleValue(),
                shape.longitude().doubleValue(), firstRemainingSection, 100).orElse(null);
        if (shapeProjection == null) return false;
        return shapeProjection.sectionIndex() > currentProjection.sectionIndex()
                || shapeProjection.sectionIndex() == currentProjection.sectionIndex()
                && shapeProjection.distanceAlongMeters() > currentProjection.distanceAlongMeters() + 5;
    }

    private void createUnavailable(TripEntity trip, TripEtaResponse currentEta, String base, Instant now) {
        String key = "REROUTE_UNAVAILABLE:" + base;
        if (notifications.findByDedupeKey(key).isPresent()) return;
        notifications.save(new TripNotificationEntity(trip, null, NotificationType.REROUTE_UNAVAILABLE,
                currentEta.status() == com.quangkhai.vehicletracking_backend.traffic.TrafficStatus.BLOCKED ? NotificationSeverity.CRITICAL : NotificationSeverity.MAJOR,
                "Không thể tạo tuyến thay thế", "Nhà cung cấp định tuyến không trả về tuyến khả dụng hoặc tuyến mới không cải thiện ETA.",
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
