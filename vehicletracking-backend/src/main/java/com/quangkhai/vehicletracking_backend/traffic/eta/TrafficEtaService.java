package com.quangkhai.vehicletracking_backend.traffic.eta;

import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
import com.quangkhai.vehicletracking_backend.simulation.motion.FlexiblePolyline;
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
    private final TripRepository trips;
    private final TripStopVisitRepository visits;
    private final VehiclePositionRepository positions;
    private final TrafficQueryService traffic;
    private final HereTrafficProperties properties;
    private final Clock operationsClock;
    private final TrafficRouteMatcher matcher = new TrafficRouteMatcher();
    private final RoutePositionMatcher positionMatcher = new RoutePositionMatcher();

    @Transactional(readOnly = true)
    public TripEtaResponse calculate(long tripId) {
        TripEntity trip = trips.findById(tripId).orElseThrow(() -> new TrafficOperationException(
                HttpStatus.NOT_FOUND, TrafficErrorCode.TRIP_NOT_FOUND, "Không tìm thấy chuyến đi."));
        RouteDetailResponse route = RouteDetailResponse.from(trip.getRoute());
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
        TrafficEnvelope<TrafficFlowSegment> flow = null;
        TrafficEnvelope<TrafficIncident> incidents = null;
        if (positionAvailable) {
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

            double remainingDistanceMeters = section.distanceMeters();
            if (positionAvailable && sectionIndex == position.get().sectionIndex()) {
                remainingDistanceMeters *= position.get().remainingFraction();
            }

            TrafficFlowSegment matchingFlow = flow == null ? null : flow.results().stream()
                    .filter(item -> matcher.matches(section.encodedPolyline(), item, properties.getCorridorRadiusMeters()))
                    .findFirst().orElse(null);
            TrafficIncident matchingIncident = incidents == null ? null : incidents.results().stream()
                    .filter(item -> !"EXPIRED".equalsIgnoreCase(item.status()))
                    .filter(item -> item.startTime() == null || !item.startTime().isAfter(calculatedAt))
                    .filter(item -> incidentAffects(section, item))
                    .findFirst().orElse(null);

            if (matchingFlow != null) {
                affected.add(new TripEtaResponse.AffectedSegment(section.sectionSequence(), section.destinationStopSequence(),
                        "FLOW", matchingFlow.id(), matchingFlow.jamFactor(), matchingFlow.traversability()));
                if (isClosed(matchingFlow.traversability())) blocked = true;
            }
            if (matchingIncident != null) {
                affected.add(new TripEtaResponse.AffectedSegment(section.sectionSequence(), section.destinationStopSequence(),
                        "INCIDENT", matchingIncident.id(), 0, matchingIncident.type()));
                if (isClosure(matchingIncident)) blocked = true;
            }

            double duration = dynamicDuration(section, matchingFlow, remainingDistanceMeters);
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

        TrafficSource etaSource = source(positionAvailable, flow, incidents);
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
        TrafficStatus status = !positionAvailable ? TrafficStatus.AVAILABLE
                : blocked ? TrafficStatus.BLOCKED : trafficStatus;
        String warning = !positionAvailable ? "VEHICLE_POSITION_UNAVAILABLE; using route snapshot"
                : blocked ? "TRAFFIC_BLOCKED" : trafficWarning;
        return new TripEtaResponse(tripId, trip.getRoute().getId(), calculatedAt, source, status,
                latestObservedAt(flow, incidents), latestFetchedAt(flow, incidents), nextStop < 0 ? null : nextStop,
                Math.max(0, Math.round(baselineCumulative)), blocked ? 0 : Math.max(0, Math.round(cumulative)), rows, affected, warning);
    }

    /**
     * Returns the fraction of baseline simulator progress that can be advanced
     * for one wall-clock second. A blocked section pauses progress; unavailable
     * or position-less traffic falls back to the immutable route snapshot.
     */
    public double simulationRate(long tripId, double baselineRemainingSeconds) {
        if (!Double.isFinite(baselineRemainingSeconds) || baselineRemainingSeconds <= 0) return 1d;
        try {
            TripEtaResponse eta = calculate(tripId);
            return rateFor(eta, baselineRemainingSeconds);
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
        return Math.max(0.1d, Math.min(1.5d, rate));
    }

    private int firstRemainingSection(List<RouteDetailResponse.RouteSectionResponse> sections, int nextStop) {
        if (nextStop < 0) return sections.size();
        for (int i = 0; i < sections.size(); i++) {
            if (sections.get(i).destinationStopSequence() >= nextStop) return i;
        }
        return sections.size();
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
        if (flow == null || flow.speedKmh() <= 0 || !Double.isFinite(flow.speedKmh())) return baseline;
        double seconds = remainingDistanceMeters / (flow.speedKmh() / 3.6);
        if (!Double.isFinite(seconds) || seconds < 0) return baseline;
        return Math.max(0, seconds);
    }

    private double baselineDuration(RouteDetailResponse.RouteSectionResponse section, double remainingDistanceMeters) {
        if (section.distanceMeters() <= 0) return Math.max(0, section.travelDurationSeconds());
        double fraction = Math.max(0, Math.min(1, remainingDistanceMeters / (double) section.distanceMeters()));
        return Math.max(0, section.travelDurationSeconds() * fraction);
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

    private TrafficSource source(boolean positionAvailable, TrafficEnvelope<?>... envelopes) {
        if (!positionAvailable) {
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
