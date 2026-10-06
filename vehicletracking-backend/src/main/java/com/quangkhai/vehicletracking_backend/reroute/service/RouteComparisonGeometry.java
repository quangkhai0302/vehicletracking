package com.quangkhai.vehicletracking_backend.reroute.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.quangkhai.vehicletracking_backend.checkin.geometry.GeofenceCrossing;
import com.quangkhai.vehicletracking_backend.reroute.entity.RouteComparisonSnapshot;
import com.quangkhai.vehicletracking_backend.reroute.entity.RouteComparisonSnapshot.Anchor;
import com.quangkhai.vehicletracking_backend.reroute.entity.RouteComparisonSnapshot.Path;
import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse.RouteSectionResponse;
import com.quangkhai.vehicletracking_backend.simulation.motion.FlexiblePolyline;
import com.quangkhai.vehicletracking_backend.simulation.motion.FlexiblePolyline.Point;
import com.quangkhai.vehicletracking_backend.simulation.motion.RouteMotion;

public final class RouteComparisonGeometry {
    private RouteComparisonGeometry() {}

    public static Path path(List<RouteSectionResponse> sections, Long duration) {
        var polylines = sections.stream().map(RouteSectionResponse::encodedPolyline).toList();
        double distance = 0;
        for (String polyline : polylines) {
            var points = FlexiblePolyline.decode(polyline);
            for (int i = 1; i < points.size(); i++) distance += distance(points.get(i - 1), points.get(i));
        }
        return new Path(polylines, Math.round(distance), duration);
    }

    public static RouteComparisonSnapshot simulation(int attempt, Long previousId, RouteMotion motion, double elapsed,
            List<RouteSectionResponse> after, long beforeDuration, long afterDuration) {
        var frame = motion.at(elapsed);
        var anchor = new Anchor(frame.latitude(), frame.longitude());
        var first = FlexiblePolyline.decode(after.getFirst().encodedPolyline()).getFirst();
        // Activation anchors can differ from creation anchors; do not manufacture historical origins.
        if (distance(new Point(anchor.latitude(), anchor.longitude()), first) > 5) throw new IllegalArgumentException("Comparison origins differ");
        return new RouteComparisonSnapshot(attempt, previousId, anchor,
                path(motion.remainingSections(elapsed), beforeDuration), path(after, afterDuration));
    }

    /** Clips only the next destination's old leg; ambiguous loop matches are rejected. */
    public static RouteComparisonSnapshot gps(int attempt, Long previousId, List<RouteSectionResponse> before,
            List<RouteSectionResponse> after, double latitude, double longitude, long beforeDuration, long afterDuration) {
        int destination = after.getFirst().destinationStopSequence();
        var anchor = new Point(latitude, longitude);
        if (distance(anchor, FlexiblePolyline.decode(after.getFirst().encodedPolyline()).getFirst()) > 5)
            throw new IllegalArgumentException("Comparison origins differ");
        record Match(int section, int segment, double ratio, double gap, double walked) {}
        var matches = new ArrayList<Match>();
        double walked = 0;
        for (int s = 0; s < before.size(); s++) {
            var points = FlexiblePolyline.decode(before.get(s).encodedPolyline());
            for (int i = 1; i < points.size(); i++) {
                var a = points.get(i - 1); var b = points.get(i);
                double length = distance(a, b);
                if (before.get(s).destinationStopSequence() == destination) {
                    double scale = Math.cos(Math.toRadians(latitude));
                    double ax = (a.longitude() - longitude) * scale, ay = a.latitude() - latitude;
                    double dx = (b.longitude() - a.longitude()) * scale, dy = b.latitude() - a.latitude();
                    double squared = dx * dx + dy * dy;
                    double ratio = squared == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / squared));
                    var projected = new Point(a.latitude() + ratio * (b.latitude() - a.latitude()), a.longitude() + ratio * (b.longitude() - a.longitude()));
                    matches.add(new Match(s, i, ratio, distance(anchor, projected), walked + length * ratio));
                }
                walked += length;
            }
        }
        matches.sort(Comparator.comparingDouble(Match::gap));
        if (matches.isEmpty() || matches.getFirst().gap() > 5) throw new IllegalArgumentException("No comparable old leg");
        var match = matches.getFirst();
        if (matches.stream().skip(1).anyMatch(m -> m.gap() <= match.gap() + 1 && Math.abs(m.walked() - match.walked()) > 30))
            throw new IllegalArgumentException("Ambiguous comparison origin");
        var first = before.get(match.section());
        var points = FlexiblePolyline.decode(first.encodedPolyline());
        var retained = new ArrayList<Point>(); retained.add(anchor); retained.addAll(points.subList(match.segment(), points.size()));
        var sections = new ArrayList<RouteSectionResponse>();
        if (retained.size() > 1) sections.add(new RouteSectionResponse(1, destination, FlexiblePolyline.encode(retained), 0, 0, 0));
        sections.addAll(before.subList(match.section() + 1, before.size()));
        return new RouteComparisonSnapshot(attempt, previousId, new Anchor(latitude, longitude),
                path(sections, beforeDuration), path(after, afterDuration));
    }

    private static double distance(Point a, Point b) {
        return GeofenceCrossing.distance(new GeofenceCrossing.Point(a.latitude(), a.longitude()),
                new GeofenceCrossing.Point(b.latitude(), b.longitude()));
    }
}
