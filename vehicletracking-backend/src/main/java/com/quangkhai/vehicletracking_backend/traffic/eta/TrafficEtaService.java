package com.quangkhai.vehicletracking_backend.traffic.eta;

import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
import com.quangkhai.vehicletracking_backend.simulation.motion.FlexiblePolyline;
import com.quangkhai.vehicletracking_backend.simulation.motion.RouteMotion;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.checkin.entity.TripStopVisitEntity;
import com.quangkhai.vehicletracking_backend.checkin.repository.TripStopVisitRepository;
import com.quangkhai.vehicletracking_backend.telemetry.entity.VehiclePositionEntity;
import com.quangkhai.vehicletracking_backend.telemetry.repository.VehiclePositionRepository;
import com.quangkhai.vehicletracking_backend.config.HereTrafficProperties;
import com.quangkhai.vehicletracking_backend.traffic.*;
import com.quangkhai.vehicletracking_backend.traffic.matching.RoutePositionMatcher;
import com.quangkhai.vehicletracking_backend.traffic.matching.TrafficRouteMatcher;
import com.quangkhai.vehicletracking_backend.traffic.service.TrafficQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TrafficEtaService {
    /** A zero HERE speed is treated as crawling traffic, rather than silently restoring free-flow timing. */
    private static final double MINIMUM_OPEN_FLOW_SPEED_KMH = 1d;
    private final TripRepository trips;
    private final TripStopVisitRepository visits;
    private final VehiclePositionRepository positions;
    private final TrafficQueryService traffic;
    private final HereTrafficProperties properties;
    private final Clock operationsClock;
    private final com.quangkhai.vehicletracking_backend.reroute.service.TripRouteGeometryService geometry;
    private final TrafficRouteMatcher matcher = new TrafficRouteMatcher();
    private final RoutePositionMatcher positionMatcher = new RoutePositionMatcher();
    // Cache geometry matches, never ETA: position/check-ins remain fresh on every calculation.
    // Bounded and shared by HTTP, simulation and reroute callers. The lock coalesces misses.
    private final Map<MatchKey, List<FlowMatch>> matchCache = new LinkedHashMap<>(16, 0.75f, true);
    private record MatchKey(String polyline, List<TrafficFlowSegment> flows, double radius) {}
    private record RecentEta(int attempt, com.quangkhai.vehicletracking_backend.trip.entity.TripStatus status,
                             long storedAt, TripEtaResponse value) {}
    private final Map<Long, RecentEta> recentEtas = new LinkedHashMap<>();

    /** Non-blocking with respect to provider/matching work; never calculates an ETA. */
    public TripEtaResponse latestForSnapshot(TripEntity trip) {
        synchronized (recentEtas) {
            RecentEta entry = recentEtas.get(trip.getId());
            if (entry == null || entry.attempt() != trip.getAttemptNumber() || entry.status() != trip.getStatus()
                    || System.nanoTime() - entry.storedAt() > 10_000_000_000L) return null;
            return entry.value();
        }
    }

    @Transactional(readOnly = true)
    public TripEtaResponse calculate(long tripId) {
        return calculate(tripId, false);
    }

    /**
     * Calculates a fresh schedule when a trip starts.  At that point a real
     * vehicle position may not exist yet, but the route corridor is still a
     * valid query area for the current HERE traffic snapshot.
     */
    @Transactional(readOnly = true)
    public TripEtaResponse calculateAtStart(long tripId) {
        return calculate(tripId, true);
    }

    private TripEtaResponse calculate(long tripId, boolean includeTrafficWithoutPosition) {
        TripEntity trip = trips.findById(tripId).orElseThrow(() -> new TrafficOperationException(
                HttpStatus.NOT_FOUND, TrafficErrorCode.TRIP_NOT_FOUND, "Không tìm thấy chuyến đi."));
        RouteDetailResponse route = geometry.route(trip);
        if (route.stops().size() < 2 || route.sections().isEmpty()) {
            throw new TrafficOperationException(HttpStatus.CONFLICT, TrafficErrorCode.ETA_UNAVAILABLE,
                    "Chuyến chưa có đủ geometry tuyến để tính ETA.");
        }
        Set<Integer> checkedIn = new HashSet<>();
        Map<Integer, Instant> actualAt = new HashMap<>();
        for (TripStopVisitEntity visit : visits.findAllByTripIdOrderByStopSequenceAsc(tripId)) {
            checkedIn.add(visit.getStopSequence());
            actualAt.put(visit.getStopSequence(), visit.getActualArrivalAt());
        }
        int nextStop = route.stops().stream().filter(stop -> !checkedIn.contains(stop.sequenceNumber()))
                .mapToInt(RouteDetailResponse.RouteStopResponse::sequenceNumber).findFirst().orElse(-1);

        Instant calculatedAt = operationsClock.instant();
        List<RouteDetailResponse.RouteSectionResponse> sections = route.sections();
        int firstRemainingSection = firstRemainingSection(sections, nextStop);
        var position = currentPosition(trip, sections, firstRemainingSection);
        boolean positionAvailable = position.isPresent();
        boolean trafficRequested = positionAvailable || includeTrafficWithoutPosition;
        TrafficEnvelope<TrafficFlowSegment> flow = null;
        TrafficEnvelope<TrafficIncident> incidents = null;
        if (trafficRequested) {
            TrafficBounds bounds = bounds(route, nextStop);
            flow = traffic.flowForEta(bounds);
            incidents = traffic.incidentsForEta(bounds);
        }

        List<TripEtaResponse.AffectedSegment> affected = new ArrayList<>();
        Map<Integer, Double> etaByStop = new LinkedHashMap<>();
        double cumulative = 0;
        double baselineCumulative = 0;
        boolean blocked = false;
        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            RouteDetailResponse.RouteSectionResponse section = sections.get(sectionIndex);
            if (nextStop < 0 || section.destinationStopSequence() < nextStop) continue;
            if (positionAvailable && sectionIndex < position.get().sectionIndex()) continue;

            double remainingFraction = 1d;
            double remainingDistanceMeters = section.distanceMeters();
            if (positionAvailable && sectionIndex == position.get().sectionIndex()) {
                remainingFraction = position.get().remainingFraction();
                remainingDistanceMeters *= remainingFraction;
            }

            List<FlowMatch> matchingFlows = matchingFlows(section, flow);
            TrafficIncident matchingIncident = bestMatchingIncident(section, incidents, calculatedAt);

            for (FlowMatch match : matchingFlows) {
                TrafficFlowSegment matchingFlow = match.flow();
                affected.add(new TripEtaResponse.AffectedSegment(section.sectionSequence(), section.destinationStopSequence(),
                        "FLOW", matchingFlow.id(), matchingFlow.jamFactor(), matchingFlow.traversability()));
                if (isClosed(matchingFlow.traversability())) blocked = true;
            }
            if (matchingIncident != null) {
                affected.add(new TripEtaResponse.AffectedSegment(section.sectionSequence(), section.destinationStopSequence(),
                        "INCIDENT", matchingIncident.id(), 0, matchingIncident.type()));
                if (isClosure(matchingIncident)) blocked = true;
            }

            double duration = trafficDuration(section, matchingFlows, remainingFraction, remainingDistanceMeters);
            baselineCumulative += baselineDuration(section, remainingDistanceMeters);
            if (blocked) duration = 0;
            cumulative += duration;
            etaByStop.merge(section.destinationStopSequence(), cumulative, Math::max);
            boolean finalSectionForStop = sectionIndex == sections.size() - 1
                    || sections.get(sectionIndex + 1).destinationStopSequence() != section.destinationStopSequence();
            if (!blocked && finalSectionForStop) {
                var stop = route.stops().stream().filter(item -> item.sequenceNumber() == section.destinationStopSequence()).findFirst().orElse(null);
                if (stop != null) cumulative += stop.dwellDurationSeconds();
            }
            if (finalSectionForStop) {
                var stop = route.stops().stream().filter(item -> item.sequenceNumber() == section.destinationStopSequence()).findFirst().orElse(null);
                if (stop != null) baselineCumulative += stop.dwellDurationSeconds();
            }
        }

        TrafficSource etaSource = source(trafficRequested, flow, incidents);
        List<TripEtaResponse.EtaStop> rows = new ArrayList<>();
        for (var stop : route.stops()) {
            if (checkedIn.contains(stop.sequenceNumber())) {
                rows.add(new TripEtaResponse.EtaStop(stop.sequenceNumber(), stop.stationName(), "CHECKED_IN", null, null,
                        actualAt.get(stop.sequenceNumber()), etaSource));
                continue;
            }
            Double eta = etaByStop.get(stop.sequenceNumber());
            if (eta == null) continue;
            rows.add(new TripEtaResponse.EtaStop(stop.sequenceNumber(), stop.stationName(),
                    stop.sequenceNumber() == nextStop ? "NEXT" : "PLANNED",
                    blocked ? null : calculatedAt.plusSeconds(Math.max(0, Math.round(eta))),
                    blocked ? null : Math.max(0, Math.round(eta)), null, etaSource));
        }

        TrafficSource source = etaSource;
        TrafficStatus trafficStatus = status(flow, incidents);
        String trafficWarning = warning(flow, incidents);
        TrafficStatus status = blocked ? TrafficStatus.BLOCKED
                : trafficRequested ? trafficStatus : TrafficStatus.AVAILABLE;
        String warning = blocked ? "TRAFFIC_BLOCKED"
                : trafficRequested ? trafficWarning : "VEHICLE_POSITION_UNAVAILABLE; using route snapshot";
        TripEtaResponse response = new TripEtaResponse(tripId, trip.getRoute().getId(), calculatedAt, source, status,
                latestObservedAt(flow, incidents), latestFetchedAt(flow, incidents), nextStop < 0 ? null : nextStop,
                Math.max(0, Math.round(baselineCumulative)), blocked ? 0 : Math.max(0, Math.round(cumulative)), rows, affected, warning);
        RecentEta entry = new RecentEta(trip.getAttemptNumber(), trip.getStatus(), System.nanoTime(), response);
        Runnable publish = () -> {
            synchronized (recentEtas) {
                if (recentEtas.size() >= 128) recentEtas.remove(recentEtas.keySet().iterator().next());
                recentEtas.put(tripId, entry);
            }
        };
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() { publish.run(); }
                    });
        } else publish.run();
        return response;
    }

    /**
     * Returns the fraction of baseline simulator progress that can be advanced
     * for one wall-clock second. A blocked section pauses progress; unavailable
     * or position-less traffic falls back to the immutable route snapshot.
     */
    public double simulationRate(long tripId, double baselineRemainingSeconds) {
        if (!Double.isFinite(baselineRemainingSeconds) || baselineRemainingSeconds <= 0) return 1d;
        try {
            return currentSectionRate(tripId);
        } catch (RuntimeException ignored) {
            return 1d;
        }
    }

    static double rateFor(TripEtaResponse eta, double baselineRemainingSeconds) {
        if (eta == null || !Double.isFinite(baselineRemainingSeconds) || baselineRemainingSeconds <= 0) return 1d;
        if (eta.status() == TrafficStatus.BLOCKED) return 0d;
        if (eta.source() == TrafficSource.ROUTE_SNAPSHOT || eta.status() == TrafficStatus.UNAVAILABLE
                || eta.totalRemainingSeconds() <= 0) return 1d;
        double rate = baselineRemainingSeconds / eta.totalRemainingSeconds();
        if (!Double.isFinite(rate)) return 1d;
        return clampRate(rate);
    }

    /**
     * Simulation progresses with the speed of the section under the vehicle,
     * not with an average factor derived from every remaining section.
     */
    private double currentSectionRate(long tripId) {
        TripEntity trip = trips.findById(tripId).orElseThrow(() -> new TrafficOperationException(
                HttpStatus.NOT_FOUND, TrafficErrorCode.TRIP_NOT_FOUND, "Không tìm thấy chuyến đi."));
        RouteDetailResponse route = geometry.route(trip);
        List<RouteDetailResponse.RouteSectionResponse> sections = route.sections();
        if (sections.isEmpty()) return 1d;

        int nextStop = nextStop(tripId, route);
        var latest = positions.findById(trip.getVehicle().getId()).orElse(null);
        if (latest != null && latest.getSample() != null) {
            var sample = latest.getSample();
            if (sample.getSource() == com.quangkhai.vehicletracking_backend.telemetry.entity.TelemetrySource.SIMULATOR
                    && trip.getId().equals(sample.getTripId()) && sample.getAttemptNumber() == trip.getAttemptNumber()
                    && sample.getSimulatedAt() != null) {
                double elapsed = java.time.Duration.between(trip.getScheduledDepartureAt(), sample.getSimulatedAt()).toNanos() / 1_000_000_000d;
                var frame = geometry.resolve(trip).motion().at(elapsed);
                if (frame.dwelling() || frame.finished()) return 1d;
                // Check-in at the edge of a station must not switch the speed
                // lookup to the next road while the vehicle is still approaching.
                nextStop = frame.nextStopSequence();
            }
        }
        int firstRemainingSection = firstRemainingSection(sections, nextStop);
        var position = currentPosition(trip, sections, firstRemainingSection);
        if (position.isEmpty()) return 1d;

        TrafficBounds bounds = bounds(route, nextStop);
        TrafficEnvelope<TrafficFlowSegment> flow = traffic.flowForEta(bounds);
        TrafficEnvelope<TrafficIncident> incidents = traffic.incidentsForEta(bounds);
        if (!isUsableTraffic(flow) && !isUsableTraffic(incidents)) return 1d;

        RouteDetailResponse.RouteSectionResponse section = sections.get(position.get().sectionIndex());
        TrafficIncident incident = bestMatchingIncident(section, incidents, operationsClock.instant());
        if (incident != null && isClosure(incident)) return 0d;

        VehiclePositionEntity current = positions.findById(trip.getVehicle().getId()).orElse(null);
        if (current == null || current.getSample() == null) return 1d;
        TrafficFlowSegment matchingFlow = bestMatchingFlowAtPosition(matchingFlows(section, flow),
                current.getSample().getLatitude(), current.getSample().getLongitude());
        if (matchingFlow == null) return 1d;
        if (isClosed(matchingFlow.traversability())) return 0d;

        // RouteMotion moves along decoded geometry. Match that distance basis,
        // including at the end of a section, so the resulting speed equals HERE's
        // local flow speed rather than a capped multiple of a route-wide average.
        double speed = matchingFlow.speedKmh();
        if (!Double.isFinite(speed) || speed < 0 || speed > 500) return 1d;
        double seconds = position.get().geometryLengthMeters()
                / (Math.max(MINIMUM_OPEN_FLOW_SPEED_KMH, speed) / 3.6d);
        return rateForSection(freeFlowDurationSeconds(section), seconds);
    }

    static double rateForSection(double baselineDurationSeconds, double trafficDurationSeconds) {
        if (!Double.isFinite(baselineDurationSeconds) || baselineDurationSeconds <= 0
                || !Double.isFinite(trafficDurationSeconds) || trafficDurationSeconds <= 0) return 1d;
        return clampRate(baselineDurationSeconds / trafficDurationSeconds);
    }

    private static double clampRate(double rate) {
        if (!Double.isFinite(rate)) return 1d;
        return rate > 0 ? rate : 1d;
    }

    private int firstRemainingSection(List<RouteDetailResponse.RouteSectionResponse> sections, int nextStop) {
        if (nextStop < 0) return sections.size();
        for (int i = 0; i < sections.size(); i++) {
            if (sections.get(i).destinationStopSequence() >= nextStop) return i;
        }
        return sections.size();
    }

    private int nextStop(long tripId, RouteDetailResponse route) {
        Set<Integer> checkedIn = visits.findAllByTripIdOrderByStopSequenceAsc(tripId).stream()
                .map(TripStopVisitEntity::getStopSequence)
                .collect(java.util.stream.Collectors.toSet());
        return route.stops().stream().filter(stop -> !checkedIn.contains(stop.sequenceNumber()))
                .mapToInt(RouteDetailResponse.RouteStopResponse::sequenceNumber).findFirst().orElse(-1);
    }

    private Optional<RoutePositionMatcher.Projection> currentPosition(TripEntity trip,
                                                                        List<RouteDetailResponse.RouteSectionResponse> sections,
                                                                        int firstRemainingSection) {
        if (firstRemainingSection >= sections.size()) return Optional.empty();
        VehiclePositionEntity position = positions.findById(trip.getVehicle().getId()).orElse(null);
        if (position == null || position.getSample() == null || !trip.getId().equals(position.getSample().getTripId())) {
            return Optional.empty();
        }
        var sample = position.getSample();
        if (sample.getAttemptNumber() != trip.getAttemptNumber()) return Optional.empty();
        return positionMatcher.project(sections, sample.getLatitude(), sample.getLongitude(), firstRemainingSection,
                properties.getCorridorRadiusMeters());
    }

    private TrafficBounds bounds(RouteDetailResponse route, int nextStop) {
        List<FlexiblePolyline.Point> geometry = new ArrayList<>();
        for (var section : route.sections()) {
            if (nextStop < 0 || section.destinationStopSequence() < nextStop) continue;
            try {
                geometry.addAll(FlexiblePolyline.decode(section.encodedPolyline()));
            } catch (RuntimeException ignored) {
                // The normal route validator already rejects malformed geometry; use stop snapshots as a safe fallback.
            }
        }
        double west = geometry.stream().mapToDouble(FlexiblePolyline.Point::longitude).min().orElseGet(
                () -> route.stops().stream().filter(stop -> nextStop < 0 || stop.sequenceNumber() >= nextStop)
                        .mapToDouble(stop -> stop.longitude().doubleValue()).min().orElse(0));
        double east = geometry.stream().mapToDouble(FlexiblePolyline.Point::longitude).max().orElseGet(
                () -> route.stops().stream().filter(stop -> nextStop < 0 || stop.sequenceNumber() >= nextStop)
                        .mapToDouble(stop -> stop.longitude().doubleValue()).max().orElse(0));
        double south = geometry.stream().mapToDouble(FlexiblePolyline.Point::latitude).min().orElseGet(
                () -> route.stops().stream().filter(stop -> nextStop < 0 || stop.sequenceNumber() >= nextStop)
                        .mapToDouble(stop -> stop.latitude().doubleValue()).min().orElse(0));
        double north = geometry.stream().mapToDouble(FlexiblePolyline.Point::latitude).max().orElseGet(
                () -> route.stops().stream().filter(stop -> nextStop < 0 || stop.sequenceNumber() >= nextStop)
                        .mapToDouble(stop -> stop.latitude().doubleValue()).max().orElse(0));
        double margin = 0.002;
        try {
            return TrafficBounds.of(Math.max(-180, west - margin), Math.max(-90, south - margin),
                    Math.min(180, east + margin), Math.min(90, north + margin), properties.getMaxBboxSpanHundredths());
        } catch (TrafficOperationException ex) {
            return TrafficBounds.of(Math.max(-180, west), Math.max(-90, south),
                    Math.min(180, east + Math.max(margin, 0.0001)), Math.min(90, north + Math.max(margin, 0.0001)),
                    Integer.MAX_VALUE);
        }
    }

    private double dynamicDuration(RouteDetailResponse.RouteSectionResponse section, TrafficFlowSegment flow,
                                   double remainingDistanceMeters) {
        double baseline = baselineDuration(section, remainingDistanceMeters);
        if (remainingDistanceMeters <= 0) return 0;
        if (flow == null || !Double.isFinite(flow.speedKmh()) || flow.speedKmh() < 0) return baseline;
        double speedKmh = Math.max(MINIMUM_OPEN_FLOW_SPEED_KMH, flow.speedKmh());
        double seconds = remainingDistanceMeters / (speedKmh / 3.6);
        if (!Double.isFinite(seconds) || seconds < 0) return baseline;
        return Math.max(0, seconds);
    }

    private synchronized List<FlowMatch> matchingFlows(RouteDetailResponse.RouteSectionResponse section,
                                          TrafficEnvelope<TrafficFlowSegment> flow) {
        if (flow == null || flow.results().isEmpty()) return List.of();
        MatchKey key = new MatchKey(section.encodedPolyline(), flow.results(), properties.getCorridorRadiusMeters());
        List<FlowMatch> cached = matchCache.get(key);
        if (cached != null) return cached;
        List<FlexiblePolyline.Point> routePoints;
        try {
            routePoints = FlexiblePolyline.decode(section.encodedPolyline());
        } catch (RuntimeException ignored) {
            return List.of();
        }
        List<FlowMatch> result = flow.results().stream()
                .map(candidate -> new FlowMatch(candidate, matcher.matchDecodedDistanceMeters(
                        routePoints, candidate, properties.getCorridorRadiusMeters())))
                .filter(match -> Double.isFinite(match.distanceMeters()))
                .sorted(Comparator.comparingDouble(FlowMatch::distanceMeters))
                .toList();
        if (matchCache.size() >= 16) matchCache.remove(matchCache.keySet().iterator().next());
        matchCache.put(key, result);
        return result;
    }

    private TrafficFlowSegment bestMatchingFlowAtPosition(List<FlowMatch> matches, double latitude, double longitude) {
        TrafficFlowSegment nearest = null;
        double best = Double.POSITIVE_INFINITY;
        // This runs for every route edge; avoid allocating a record per candidate.
        for (FlowMatch match : matches) {
            double distance = matcher.distanceToFlowMeters(latitude, longitude, match.flow());
            if (Double.isFinite(distance) && distance <= properties.getCorridorRadiusMeters() && distance < best) {
                best = distance;
                nearest = match.flow();
            }
        }
        return nearest;
    }

    private double trafficDuration(RouteDetailResponse.RouteSectionResponse section, List<FlowMatch> matches,
                                   double remainingFraction, double remainingDistanceMeters) {
        if (matches.isEmpty() || remainingDistanceMeters <= 0) return baselineDuration(section, remainingDistanceMeters);
        List<FlexiblePolyline.Point> points;
        try {
            points = FlexiblePolyline.decode(section.encodedPolyline());
        } catch (RuntimeException ignored) {
            return dynamicDuration(section, matches.getFirst().flow(), remainingDistanceMeters);
        }
        if (points.size() < 2) return dynamicDuration(section, matches.getFirst().flow(), remainingDistanceMeters);

        double[] distances = new double[points.size()];
        for (int index = 1; index < points.size(); index++) {
            distances[index] = distances[index - 1] + RouteMotion.distance(points.get(index - 1), points.get(index));
        }
        double geometryLength = distances[distances.length - 1];
        if (geometryLength <= 0 || !Double.isFinite(geometryLength)) {
            return dynamicDuration(section, matches.getFirst().flow(), remainingDistanceMeters);
        }

        double startAt = geometryLength * Math.max(0d, Math.min(1d, 1d - remainingFraction));
        double scale = section.distanceMeters() <= 0 ? 1d : section.distanceMeters() / geometryLength;
        long freeFlowSeconds = freeFlowDurationSeconds(section);
        double baselineSpeedKmh = freeFlowSeconds <= 0 ? 0d
                : section.distanceMeters() / (double) freeFlowSeconds * 3.6d;
        double result = 0d;
        for (int index = 1; index < points.size(); index++) {
            double from = Math.max(startAt, distances[index - 1]);
            double to = distances[index];
            if (to <= from) continue;
            double ratio = ((from + to) / 2d - distances[index - 1]) / (distances[index] - distances[index - 1]);
            FlexiblePolyline.Point midpoint = interpolate(points.get(index - 1), points.get(index), ratio);
            TrafficFlowSegment flow = bestMatchingFlowAtPosition(matches, midpoint.latitude(), midpoint.longitude());
            double speedKmh = flow == null ? baselineSpeedKmh : Math.max(MINIMUM_OPEN_FLOW_SPEED_KMH, flow.speedKmh());
            if (!Double.isFinite(speedKmh) || speedKmh <= 0) return baselineDuration(section, remainingDistanceMeters);
            result += ((to - from) * scale) / (speedKmh / 3.6d);
        }
        return Double.isFinite(result) && result >= 0 ? result : baselineDuration(section, remainingDistanceMeters);
    }

    private FlexiblePolyline.Point interpolate(FlexiblePolyline.Point from, FlexiblePolyline.Point to, double ratio) {
        double fraction = Math.max(0d, Math.min(1d, ratio));
        double deltaLon = ((to.longitude() - from.longitude() + 540d) % 360d) - 180d;
        return new FlexiblePolyline.Point(from.latitude() + (to.latitude() - from.latitude()) * fraction,
                ((from.longitude() + deltaLon * fraction + 540d) % 360d) - 180d);
    }

    private TrafficIncident bestMatchingIncident(RouteDetailResponse.RouteSectionResponse section,
                                                  TrafficEnvelope<TrafficIncident> incidents, Instant calculatedAt) {
        if (incidents == null) return null;
        return incidents.results().stream()
                .filter(item -> !"EXPIRED".equalsIgnoreCase(item.status()))
                .filter(item -> item.startTime() == null || !item.startTime().isAfter(calculatedAt))
                .filter(item -> incidentAffects(section, item))
                .findFirst().orElse(null);
    }

    private record FlowMatch(TrafficFlowSegment flow, double distanceMeters) {}

    private double baselineDuration(RouteDetailResponse.RouteSectionResponse section, double remainingDistanceMeters) {
        long freeFlowSeconds = freeFlowDurationSeconds(section);
        if (section.distanceMeters() <= 0) return Math.max(0, freeFlowSeconds);
        double fraction = Math.max(0, Math.min(1, remainingDistanceMeters / (double) section.distanceMeters()));
        return Math.max(0, freeFlowSeconds * fraction);
    }

    private long freeFlowDurationSeconds(RouteDetailResponse.RouteSectionResponse section) {
        long base = section.baseTravelDurationSeconds();
        // Keep routes created before baseDuration was persisted usable. New
        // HERE routes always provide this field, so live traffic is applied
        // exactly once on top of the free-flow duration.
        return base > 0 ? base : Math.max(0, section.travelDurationSeconds());
    }

    private boolean incidentAffects(RouteDetailResponse.RouteSectionResponse section, TrafficIncident incident) {
        if (incident.points().isEmpty()) return false;
        TrafficFlowSegment shape = new TrafficFlowSegment(incident.id(), incident.description(), 0, incident.points(),
                0, 0, 0, "unknown", null);
        return matcher.matches(section.encodedPolyline(), shape, properties.getCorridorRadiusMeters());
    }

    private boolean isClosed(String traversability) {
        return traversability != null && (traversability.equalsIgnoreCase("closed")
                || traversability.equalsIgnoreCase("reversibleNotRoutable"));
    }

    private boolean isClosure(TrafficIncident incident) {
        String type = incident.type() == null ? "" : incident.type().toLowerCase(Locale.ROOT);
        return type.contains("closure") || type.contains("blocked") || type.contains("road_closure");
    }

    private TrafficSource source(boolean trafficRequested, TrafficEnvelope<?>... envelopes) {
        if (!trafficRequested) {
            return TrafficSource.ROUTE_SNAPSHOT;
        }
        return Arrays.stream(envelopes)
                .filter(this::isUsableTraffic)
                .max(Comparator.comparing(TrafficEnvelope::fetchedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(TrafficEnvelope::source)
                .orElse(TrafficSource.ROUTE_SNAPSHOT);
    }

    private Instant latestFetchedAt(TrafficEnvelope<?>... envelopes) {
        return Arrays.stream(envelopes)
                .filter(this::isUsableTraffic)
                .map(TrafficEnvelope::fetchedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private Instant latestObservedAt(TrafficEnvelope<?>... envelopes) {
        return Arrays.stream(envelopes)
                .filter(this::isUsableTraffic)
                .map(TrafficEnvelope::observedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private String warning(TrafficEnvelope<?> flow, TrafficEnvelope<?> incidents) {
        var latest = latestUsable(flow, incidents).orElse(null);
        if (latest != null && latest.warning() != null && !latest.warning().isBlank()) return latest.warning();
        return latestFetchedAt(flow, incidents) == null
                ? "Traffic provider unavailable; using route snapshot" : null;
    }

    private TrafficStatus status(TrafficEnvelope<?>... envelopes) {
        return latestUsable(envelopes).map(TrafficEnvelope::status).orElse(TrafficStatus.UNAVAILABLE);
    }

    private Optional<TrafficEnvelope<?>> latestUsable(TrafficEnvelope<?>... envelopes) {
        return Arrays.stream(envelopes)
                .filter(this::isUsableTraffic)
                .max(Comparator.comparing(TrafficEnvelope::fetchedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    private boolean isUsableTraffic(TrafficEnvelope<?> envelope) {
        return envelope != null && (envelope.source() == TrafficSource.HERE_LIVE
                || envelope.source() == TrafficSource.HERE_LAST_KNOWN);
    }
}
