package com.quangkhai.vehicletracking_backend.traffic.matching;

import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
import com.quangkhai.vehicletracking_backend.simulation.motion.FlexiblePolyline;
import com.quangkhai.vehicletracking_backend.simulation.motion.RouteMotion;

import java.util.List;
import java.util.Optional;

/**
 * Projects a telemetry point onto the remaining route geometry. The projection
 * is deliberately local and read-only: it is used to trim the first remaining
 * section for ETA and never changes the immutable route snapshot.
 */
public final class RoutePositionMatcher {
    private static final double EARTH_RADIUS_METERS = 6_371_000d;

    public record Projection(int sectionIndex, double fraction, double distanceAlongMeters,
                             double geometryLengthMeters, double distanceToRouteMeters) {
        public double remainingFraction() {
            return Math.max(0d, Math.min(1d, 1d - fraction));
        }
    }

    public Optional<Projection> project(List<RouteDetailResponse.RouteSectionResponse> sections,
                                         double latitude, double longitude,
                                         int firstRemainingSectionIndex, double maxDistanceMeters) {
        if (sections == null || sections.isEmpty() || !finite(latitude) || !finite(longitude)
                || Math.abs(latitude) > 90 || Math.abs(longitude) > 180
                || !finite(maxDistanceMeters) || maxDistanceMeters < 0) {
            return Optional.empty();
        }

        int start = Math.max(0, firstRemainingSectionIndex);
        if (start >= sections.size()) return Optional.empty();

        FlexiblePolyline.Point position = new FlexiblePolyline.Point(latitude, longitude);
        Projection best = null;
        for (int sectionIndex = start; sectionIndex < sections.size(); sectionIndex++) {
            RouteDetailResponse.RouteSectionResponse section = sections.get(sectionIndex);
            if (section == null || section.encodedPolyline() == null) continue;
            List<FlexiblePolyline.Point> points;
            try {
                points = FlexiblePolyline.decode(section.encodedPolyline());
            } catch (RuntimeException ignored) {
                continue;
            }
            if (points.isEmpty()) continue;

            double[] cumulative = new double[points.size()];
            for (int i = 1; i < points.size(); i++) {
                cumulative[i] = cumulative[i - 1] + RouteMotion.distance(points.get(i - 1), points.get(i));
            }
            double geometryLength = cumulative[cumulative.length - 1];

            if (points.size() == 1) {
                double distance = RouteMotion.distance(position, points.getFirst());
                best = closer(best, new Projection(sectionIndex, 0, 0, geometryLength, distance));
                continue;
            }

            for (int i = 1; i < points.size(); i++) {
                FlexiblePolyline.Point from = points.get(i - 1);
                FlexiblePolyline.Point to = points.get(i);
                SegmentProjection candidate = projectSegment(position, from, to);
                double segmentLength = RouteMotion.distance(from, to);
                double along = cumulative[i - 1] + segmentLength * candidate.ratio();
                double fraction = geometryLength <= 0 ? 0 : along / geometryLength;
                best = closer(best, new Projection(sectionIndex, fraction, along, geometryLength,
                        candidate.distanceMeters()));
            }
        }

        return best != null && best.distanceToRouteMeters() <= maxDistanceMeters ? Optional.of(best) : Optional.empty();
    }

    private Projection closer(Projection current, Projection candidate) {
        return current == null || candidate.distanceToRouteMeters() < current.distanceToRouteMeters()
                ? candidate : current;
    }

    private SegmentProjection projectSegment(FlexiblePolyline.Point position,
                                              FlexiblePolyline.Point from,
                                              FlexiblePolyline.Point to) {
        double referenceLatitude = Math.toRadians(position.latitude());
        double metersPerDegree = EARTH_RADIUS_METERS * Math.PI / 180d;
        double longitudeScale = Math.max(1e-9, Math.cos(referenceLatitude)) * metersPerDegree;
        double ax = normalizeDeltaLongitude(from.longitude() - position.longitude()) * longitudeScale;
        double ay = (from.latitude() - position.latitude()) * metersPerDegree;
        double bx = normalizeDeltaLongitude(to.longitude() - position.longitude()) * longitudeScale;
        double by = (to.latitude() - position.latitude()) * metersPerDegree;
        double dx = bx - ax;
        double dy = by - ay;
        double denominator = dx * dx + dy * dy;
        double ratio = denominator <= 1e-9 ? 0 : Math.max(0, Math.min(1, (-ax * dx - ay * dy) / denominator));
        double distance = Math.hypot(ax + dx * ratio, ay + dy * ratio);
        return new SegmentProjection(ratio, distance);
    }

    private static boolean finite(double value) {
        return Double.isFinite(value);
    }

    private static double normalizeDeltaLongitude(double value) {
        return ((value + 540d) % 360d) - 180d;
    }

    private record SegmentProjection(double ratio, double distanceMeters) {}
}
