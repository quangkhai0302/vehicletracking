package com.quangkhai.vehicletracking_backend.reroute.service;

import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
import com.quangkhai.vehicletracking_backend.route.provider.CalculatedRoute;
import com.quangkhai.vehicletracking_backend.traffic.eta.TripEtaResponse;
import com.quangkhai.vehicletracking_backend.traffic.matching.RoutePositionMatcher;

import java.util.List;
import java.util.Locale;

/** Rejects a detour that still crosses the HERE closure which caused the reroute. */
final class RerouteCandidateValidator {
    private RerouteCandidateValidator() {}

    static boolean intersectsTriggeringClosure(CalculatedRoute candidate, TripEtaResponse eta, double radiusMeters) {
        if (candidate == null || eta == null || !Double.isFinite(radiusMeters) || radiusMeters <= 0) return false;
        List<RouteDetailResponse.RouteSectionResponse> sections = candidate.sections().stream()
                .map(section -> new RouteDetailResponse.RouteSectionResponse(
                        section.sectionSequence(), section.destinationStopSequence(), section.encodedPolyline(),
                        section.polylineEncoding(), section.distanceMeters(), section.travelDurationSeconds(),
                        section.baseTravelDurationSeconds(), section.trafficIntervals()))
                .toList();
        if (sections.isEmpty()) return false;

        RoutePositionMatcher matcher = new RoutePositionMatcher();
        for (TripEtaResponse.AffectedSegment segment : eta.affectedSegments()) {
            if (!isClosure(segment)) continue;
            for (List<Double> point : incidentPoints(segment)) {
                if (point.size() < 2 || !finite(point.get(0)) || !finite(point.get(1))) continue;
                if (matcher.project(sections, point.get(0), point.get(1), 0, radiusMeters).isPresent()) return true;
            }
        }
        return false;
    }

    private static List<List<Double>> incidentPoints(TripEtaResponse.AffectedSegment segment) {
        if (!segment.points().isEmpty()) return segment.points();
        if (segment.center().size() >= 2) return List.of(segment.center());
        return List.of();
    }

    private static boolean isClosure(TripEtaResponse.AffectedSegment segment) {
        if (!"INCIDENT".equals(segment.kind())) return false;
        String value = segment.traversability() == null ? "" : segment.traversability().toLowerCase(Locale.ROOT);
        return value.contains("closure") || value.contains("closed") || value.contains("road_closed");
    }

    private static boolean finite(Double value) {
        return value != null && Double.isFinite(value);
    }
}
