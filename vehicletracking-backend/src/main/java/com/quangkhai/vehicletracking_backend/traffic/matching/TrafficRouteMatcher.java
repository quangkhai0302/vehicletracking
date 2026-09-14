package com.quangkhai.vehicletracking_backend.traffic.matching;

import com.quangkhai.vehicletracking_backend.simulation.motion.FlexiblePolyline;
import com.quangkhai.vehicletracking_backend.simulation.motion.RouteMotion;
import com.quangkhai.vehicletracking_backend.traffic.TrafficFlowSegment;

import java.util.List;

public class TrafficRouteMatcher {
    public boolean matches(String encodedRoutePolyline, TrafficFlowSegment flow, double radiusMeters) {
        if (encodedRoutePolyline == null || flow == null || flow.points().isEmpty()) return false;
        List<FlexiblePolyline.Point> route;
        try {
            route = FlexiblePolyline.decode(encodedRoutePolyline);
        } catch (RuntimeException ex) {
            return false;
        }
        if (route.isEmpty()) return false;
        boolean spatial = false;
        for (var point : flow.points()) {
            if (point == null || point.size() < 2) continue;
            var candidate = new FlexiblePolyline.Point(point.get(0), point.get(1));
            if (distanceToPolyline(candidate, route) <= radiusMeters) {
                spatial = true;
                break;
            }
        }
        if (!spatial) {
            for (var point : route) {
                var candidate = new FlexiblePolyline.Point(point.latitude(), point.longitude());
                if (distanceToPolyline(candidate, flow.points()) <= radiusMeters) {
                    spatial = true;
                    break;
                }
            }
        }
        if (!spatial || flow.points().size() < 2 || route.size() < 2) return spatial;
        var routeStart = route.getFirst();
        var routeEnd = route.getLast();
        var flowStart = new FlexiblePolyline.Point(flow.points().getFirst().get(0), flow.points().getFirst().get(1));
        var flowEnd = new FlexiblePolyline.Point(flow.points().getLast().get(0), flow.points().getLast().get(1));
        return angleDifference(bearing(routeStart, routeEnd), bearing(flowStart, flowEnd)) <= 120;
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
