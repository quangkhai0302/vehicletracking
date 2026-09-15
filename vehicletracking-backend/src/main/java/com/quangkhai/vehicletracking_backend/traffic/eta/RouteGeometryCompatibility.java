package com.quangkhai.vehicletracking_backend.traffic.eta;

import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
import com.quangkhai.vehicletracking_backend.route.provider.CalculatedSection;
import com.quangkhai.vehicletracking_backend.simulation.motion.FlexiblePolyline;
import com.quangkhai.vehicletracking_backend.simulation.motion.RouteMotion;
import com.quangkhai.vehicletracking_backend.simulation.motion.RoutePolylineCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Prevents a refreshed ETA for a newly selected Google path from being shown
 * against the geometry that the driver/simulator is actually following.
 */
final class RouteGeometryCompatibility {
    private static final double CORRIDOR_METERS = 30d;
    private static final double MAX_OFF_ROUTE_RUN_METERS = 100d;
    private static final double MIN_COVERAGE = 0.95d;
    private static final double SAMPLE_STEP_METERS = 20d;

    private RouteGeometryCompatibility() {}

    static boolean equivalent(List<CalculatedSection> candidateSections,
                              List<RouteDetailResponse.RouteSectionResponse> activeSections,
                              int firstActiveSection,
                              double currentLatitude,
                              double currentLongitude) {
        List<FlexiblePolyline.Point> candidate = decodeCandidate(candidateSections);
        List<FlexiblePolyline.Point> active = decodeActive(activeSections, firstActiveSection);
        if (candidate.size() < 2 || active.size() < 2) return false;

        var current = new FlexiblePolyline.Point(currentLatitude, currentLongitude);
        if (nearestDistance(current, candidate) > MAX_OFF_ROUTE_RUN_METERS) return false;
        if (RouteMotion.distance(candidate.getLast(), active.getLast()) > MAX_OFF_ROUTE_RUN_METERS) return false;

        double total = 0;
        double covered = 0;
        double offRouteRun = 0;
        for (int index = 1; index < candidate.size(); index++) {
            var from = candidate.get(index - 1);
            var to = candidate.get(index);
            double length = RouteMotion.distance(from, to);
            if (!Double.isFinite(length) || length < 0 || length > 20_000) return false;
            int samples = Math.max(1, (int) Math.ceil(length / SAMPLE_STEP_METERS));
            double sampleLength = length / samples;
            for (int sample = 0; sample < samples; sample++) {
                double fraction = (sample + 0.5d) / samples;
                var point = interpolate(from, to, fraction);
                boolean inside = nearestDistance(point, active) <= CORRIDOR_METERS;
                total += sampleLength;
                if (inside) {
                    covered += sampleLength;
                    offRouteRun = 0;
                } else {
                    offRouteRun += sampleLength;
                    if (offRouteRun > MAX_OFF_ROUTE_RUN_METERS) return false;
                }
            }
        }
        return total > 0 && covered / total >= MIN_COVERAGE;
    }

    private static List<FlexiblePolyline.Point> decodeCandidate(List<CalculatedSection> sections) {
        List<FlexiblePolyline.Point> result = new ArrayList<>();
        if (sections == null) return result;
        for (var section : sections) {
            appendConnected(result, RoutePolylineCodec.decode(section.encodedPolyline(), section.polylineEncoding()));
        }
        return result;
    }

    private static List<FlexiblePolyline.Point> decodeActive(
            List<RouteDetailResponse.RouteSectionResponse> sections, int firstSection) {
        List<FlexiblePolyline.Point> result = new ArrayList<>();
        if (sections == null) return result;
        for (int index = Math.max(0, firstSection); index < sections.size(); index++) {
            var section = sections.get(index);
            appendConnected(result, RoutePolylineCodec.decode(section.encodedPolyline(), section.polylineEncoding()));
        }
        return result;
    }

    private static void appendConnected(List<FlexiblePolyline.Point> target, List<FlexiblePolyline.Point> points) {
        if (points == null || points.isEmpty()) throw new IllegalArgumentException("Route geometry is empty");
        if (!target.isEmpty() && RouteMotion.distance(target.getLast(), points.getFirst()) > MAX_OFF_ROUTE_RUN_METERS) {
            throw new IllegalArgumentException("Route geometry is disconnected");
        }
        int start = !target.isEmpty() && RouteMotion.distance(target.getLast(), points.getFirst()) < 0.5 ? 1 : 0;
        target.addAll(points.subList(start, points.size()));
    }

    private static double nearestDistance(FlexiblePolyline.Point point, List<FlexiblePolyline.Point> line) {
        double best = Double.POSITIVE_INFINITY;
        for (int index = 1; index < line.size(); index++) {
            best = Math.min(best, distanceToSegment(point, line.get(index - 1), line.get(index)));
        }
        return best;
    }

    private static double distanceToSegment(FlexiblePolyline.Point point,
                                            FlexiblePolyline.Point from,
                                            FlexiblePolyline.Point to) {
        double metersPerDegree = 6_371_000d * Math.PI / 180d;
        double longitudeScale = Math.max(1e-9, Math.cos(Math.toRadians(point.latitude()))) * metersPerDegree;
        double ax = longitudeDelta(from.longitude() - point.longitude()) * longitudeScale;
        double ay = (from.latitude() - point.latitude()) * metersPerDegree;
        double bx = longitudeDelta(to.longitude() - point.longitude()) * longitudeScale;
        double by = (to.latitude() - point.latitude()) * metersPerDegree;
        double dx = bx - ax;
        double dy = by - ay;
        double denominator = dx * dx + dy * dy;
        double ratio = denominator <= 1e-9 ? 0 : Math.max(0, Math.min(1, (-ax * dx - ay * dy) / denominator));
        return Math.hypot(ax + dx * ratio, ay + dy * ratio);
    }

    private static FlexiblePolyline.Point interpolate(FlexiblePolyline.Point from,
                                                       FlexiblePolyline.Point to,
                                                       double fraction) {
        double longitudeDelta = longitudeDelta(to.longitude() - from.longitude());
        double longitude = ((from.longitude() + longitudeDelta * fraction + 540d) % 360d) - 180d;
        return new FlexiblePolyline.Point(
                from.latitude() + (to.latitude() - from.latitude()) * fraction,
                longitude);
    }

    private static double longitudeDelta(double value) {
        return ((value + 540d) % 360d) - 180d;
    }
}
