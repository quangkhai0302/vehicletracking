package com.quangkhai.vehicletracking_backend.driverportal.service;

import com.quangkhai.vehicletracking_backend.checkin.geometry.GeofenceCrossing;
import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse.RouteSectionResponse;
import com.quangkhai.vehicletracking_backend.route.dto.RouteInstruction;
import com.quangkhai.vehicletracking_backend.simulation.motion.FlexiblePolyline;
import com.quangkhai.vehicletracking_backend.simulation.motion.FlexiblePolyline.Point;
import java.util.*;

/** Re-anchor a short-lived preview without teleporting or returning to its old origin. */
public final class DriverRouteRebaser {
    private DriverRouteRebaser() {}

    public static List<RouteSectionResponse> rebase(List<RouteSectionResponse> sections, double latitude, double longitude) {
        if (sections.isEmpty()) throw stale();
        var section = sections.getFirst();
        var points = FlexiblePolyline.decode(section.encodedPolyline());
        if (points.size() < 2) throw stale();
        // Only the first 250m of a preview is eligible. Never match a later crossing/loop.
        double best = Double.POSITIVE_INFINITY, bestRatio = 0, walked = 0, before = 0;
        int segment = -1;
        for (int i = 1; i < points.size() && walked <= 250; i++) {
            var a = points.get(i - 1); var b = points.get(i);
            double xScale = Math.cos(Math.toRadians(latitude));
            double ax = (a.longitude() - longitude) * xScale, ay = a.latitude() - latitude;
            double dx = (b.longitude() - a.longitude()) * xScale, dy = b.latitude() - a.latitude();
            double lengthSquared = dx * dx + dy * dy;
            double ratio = lengthSquared == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / lengthSquared));
            var projected = new Point(a.latitude() + (b.latitude() - a.latitude()) * ratio,
                    a.longitude() + (b.longitude() - a.longitude()) * ratio);
            double distance = distance(new Point(latitude, longitude), projected);
            if (distance < best) { best = distance; segment = i; bestRatio = ratio; before = walked; }
            walked += distance(a, b);
        }
        if (segment < 0 || best > 25) throw stale();
        var a = points.get(segment - 1); var b = points.get(segment);
        double removed = before + distance(a, b) * bestRatio;
        if (removed > 250) throw stale();
        var retained = new ArrayList<Point>();
        retained.add(new Point(latitude, longitude));
        retained.addAll(points.subList(segment, points.size()));
        double oldLength = 0, newLength = 0;
        for (int i = 1; i < points.size(); i++) oldLength += distance(points.get(i - 1), points.get(i));
        for (int i = 1; i < retained.size(); i++) newLength += distance(retained.get(i - 1), retained.get(i));
        if (newLength < 1 || oldLength <= 0) throw stale();
        double ratio = newLength / oldLength;
        int shift = segment - 1;
        var instructions = section.instructions().stream().filter(i -> i.offset() > shift)
                .map(i -> new RouteInstruction(i.action(), i.direction(), i.instruction(), i.offset() - shift)).toList();
        var result = new ArrayList<>(sections);
        result.set(0, new RouteSectionResponse(section.sectionSequence(), section.destinationStopSequence(),
                FlexiblePolyline.encode(retained), Math.max(1, Math.round(section.distanceMeters() * ratio)),
                Math.max(1, Math.round(section.travelDurationSeconds() * ratio)),
                Math.max(1, Math.round(section.baseTravelDurationSeconds() * ratio)), instructions));
        return List.copyOf(result);
    }

    private static double distance(Point a, Point b) {
        return GeofenceCrossing.distance(new GeofenceCrossing.Point(a.latitude(), a.longitude()),
                new GeofenceCrossing.Point(b.latitude(), b.longitude()));
    }
    private static IllegalArgumentException stale() { return new IllegalArgumentException("Preview position no longer applies"); }
}
