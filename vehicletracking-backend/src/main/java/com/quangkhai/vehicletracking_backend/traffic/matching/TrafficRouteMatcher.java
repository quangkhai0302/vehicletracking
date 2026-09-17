package com.quangkhai.vehicletracking_backend.traffic.matching;

import com.quangkhai.vehicletracking_backend.simulation.motion.FlexiblePolyline;
import com.quangkhai.vehicletracking_backend.simulation.motion.RouteMotion;
import com.quangkhai.vehicletracking_backend.traffic.TrafficFlowSegment;

import java.util.List;

public class TrafficRouteMatcher {
    public boolean matches(String encodedRoutePolyline, TrafficFlowSegment flow, double radiusMeters) {
        return Double.isFinite(matchDistanceMeters(encodedRoutePolyline, flow, radiusMeters));
    }

    /**
     * Returns the closest geometry distance for a compatible flow segment.
     * {@link Double#POSITIVE_INFINITY} means the segment is not on this route
     * corridor or is travelling in the opposite direction.
     */
    public double matchDistanceMeters(String encodedRoutePolyline, TrafficFlowSegment flow, double radiusMeters) {
        if (encodedRoutePolyline == null || flow == null || flow.points().isEmpty()) return Double.POSITIVE_INFINITY;
        List<FlexiblePolyline.Point> route;
        try {
            route = FlexiblePolyline.decode(encodedRoutePolyline);
        } catch (RuntimeException ex) {
            return Double.POSITIVE_INFINITY;
        }
        return matchDecodedDistanceMeters(route, flow, radiusMeters);
    }

    /** Reuse decoded route geometry across the flow candidates of one request. */
    public double matchDecodedDistanceMeters(List<FlexiblePolyline.Point> route, TrafficFlowSegment flow, double radiusMeters) {
        if (route == null || route.isEmpty() || flow == null || flow.points().isEmpty()
                || !Double.isFinite(radiusMeters) || radiusMeters < 0) return Double.POSITIVE_INFINITY;
        // Conservative latitude envelope; do not reject based on longitude (dateline/poles).
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (var point : route) {
            min = Math.min(min, point.latitude());
            max = Math.max(max, point.latitude());
        }
        double flowMin = Double.POSITIVE_INFINITY, flowMax = Double.NEGATIVE_INFINITY;
        for (var point : flow.points()) {
            if (point == null || point.size() < 2 || point.get(0) == null || !Double.isFinite(point.get(0))) continue;
            flowMin = Math.min(flowMin, point.get(0));
            flowMax = Math.max(flowMax, point.get(0));
        }
        double margin = radiusMeters / 110_000d;
        if (flowMin > max + margin || flowMax < min - margin) return Double.POSITIVE_INFINITY;
        double closest = Double.POSITIVE_INFINITY;
        for (var point : flow.points()) {
            if (point == null || point.size() < 2) continue;
            try {
                closest = Math.min(closest, distanceToPolyline(new FlexiblePolyline.Point(point.get(0), point.get(1)), route));
            } catch (RuntimeException ignored) {
                // Invalid upstream point cannot make a route match.
            }
        }
        if (closest > radiusMeters) {
            for (var point : route) {
                closest = Math.min(closest, distanceToPolyline(point, flow.points()));
            }
        }
        if (!Double.isFinite(closest) || closest > radiusMeters) return Double.POSITIVE_INFINITY;
        if (flow.points().size() < 2 || route.size() < 2) return closest;
        try {
            var flowStart = new FlexiblePolyline.Point(flow.points().getFirst().get(0), flow.points().getFirst().get(1));
            var flowEnd = new FlexiblePolyline.Point(flow.points().getLast().get(0), flow.points().getLast().get(1));
            double routeBearing = closestSegmentBearing(flowStart, route);
            return !Double.isFinite(routeBearing) || angleDifference(routeBearing, bearing(flowStart, flowEnd)) <= 120
                    ? closest : Double.POSITIVE_INFINITY;
        } catch (RuntimeException ignored) {
            return Double.POSITIVE_INFINITY;
        }
    }

    /** Distance from a vehicle position to a HERE flow shape. */
    public double distanceToFlowMeters(double latitude, double longitude, TrafficFlowSegment flow) {
        if (flow == null || flow.points().isEmpty() || !Double.isFinite(latitude) || !Double.isFinite(longitude)) {
            return Double.POSITIVE_INFINITY;
        }
        try {
            return distanceToPolyline(new FlexiblePolyline.Point(latitude, longitude), flow.points());
        } catch (RuntimeException ignored) {
            return Double.POSITIVE_INFINITY;
        }
    }

    private double distanceToPolyline(FlexiblePolyline.Point point, List<?> polyline) {
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < polyline.size(); i++) {
            FlexiblePolyline.Point current;
            try {
                current = toPoint(polyline.get(i));
            } catch (RuntimeException ex) {
                continue;
            }
            best = Math.min(best, RouteMotion.distance(point, current));
            if (i > 0) {
                try {
                    best = Math.min(best, distanceToSegment(point, toPoint(polyline.get(i - 1)), current));
                } catch (RuntimeException ignored) {
                    // Ignore malformed upstream points; a valid point can still match the corridor.
                }
            }
        }
        return best;
    }

    private double closestSegmentBearing(FlexiblePolyline.Point point, List<FlexiblePolyline.Point> polyline) {
        double best = Double.POSITIVE_INFINITY;
        double result = Double.NaN;
        for (int index = 1; index < polyline.size(); index++) {
            FlexiblePolyline.Point from = polyline.get(index - 1);
            FlexiblePolyline.Point to = polyline.get(index);
            double distance = distanceToSegment(point, from, to);
            if (distance < best && RouteMotion.distance(from, to) > 0.01d) {
                best = distance;
                result = bearing(from, to);
            }
        }
        return result;
    }

    private FlexiblePolyline.Point toPoint(Object value) {
        if (value instanceof FlexiblePolyline.Point point) return point;
        @SuppressWarnings("unchecked") List<Double> pair = (List<Double>) value;
        return new FlexiblePolyline.Point(pair.get(0), pair.get(1));
    }

    private double distanceToSegment(FlexiblePolyline.Point p, FlexiblePolyline.Point a, FlexiblePolyline.Point b) {
        double scale = Math.cos(Math.toRadians((a.latitude() + b.latitude() + p.latitude()) / 3));
        double ax = a.longitude() * scale, ay = a.latitude();
        double bx = b.longitude() * scale, by = b.latitude();
        double px = p.longitude() * scale, py = p.latitude();
        double dx = bx - ax, dy = by - ay;
        double denominator = dx * dx + dy * dy;
        double ratio = denominator == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / denominator));
        return RouteMotion.distance(p, new FlexiblePolyline.Point(ay + (by - ay) * ratio,
                (ax + (bx - ax) * ratio) / (scale == 0 ? 1 : scale)));
    }

    private double bearing(FlexiblePolyline.Point from, FlexiblePolyline.Point to) {
        double dLon = Math.toRadians(to.longitude() - from.longitude());
        double y = Math.sin(dLon) * Math.cos(Math.toRadians(to.latitude()));
        double x = Math.cos(Math.toRadians(from.latitude())) * Math.sin(Math.toRadians(to.latitude()))
                - Math.sin(Math.toRadians(from.latitude())) * Math.cos(Math.toRadians(to.latitude())) * Math.cos(dLon);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    private double angleDifference(double left, double right) {
        double difference = Math.abs(left - right) % 360;
        return Math.min(difference, 360 - difference);
    }
}
