package com.quangkhai.vehicletracking_backend.simulation.motion;

import java.util.ArrayList;
import java.util.List;

/** Decoder for Google's Encoded Polyline Algorithm Format (precision 5). */
public final class GooglePolyline {
    private GooglePolyline() {}

    public static List<FlexiblePolyline.Point> decode(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > 2_000_000) throw invalid();
        List<FlexiblePolyline.Point> points = new ArrayList<>();
        long latitude = 0;
        long longitude = 0;
        int index = 0;
        while (index < encoded.length()) {
            Value lat = read(encoded, index);
            index = lat.nextIndex();
            Value lng = read(encoded, index);
            index = lng.nextIndex();
            latitude = Math.addExact(latitude, signed(lat.value()));
            longitude = Math.addExact(longitude, signed(lng.value()));
            double decodedLat = latitude / 100000d;
            double decodedLng = longitude / 100000d;
            if (!Double.isFinite(decodedLat) || !Double.isFinite(decodedLng)
                    || Math.abs(decodedLat) > 90 || Math.abs(decodedLng) > 180) throw invalid();
            points.add(new FlexiblePolyline.Point(decodedLat, decodedLng));
        }
        if (points.isEmpty()) throw invalid();
        return List.copyOf(points);
    }

    private static Value read(String encoded, int start) {
        long result = 0;
        int shift = 0;
        int index = start;
        while (index < encoded.length() && shift <= 60) {
            int value = encoded.charAt(index++) - 63;
            if (value < 0 || value > 63) throw invalid();
            result |= (long) (value & 0x1f) << shift;
            if (value < 0x20) return new Value(result, index);
            shift += 5;
        }
        throw invalid();
    }

    private static long signed(long value) {
        return (value & 1) != 0 ? ~(value >> 1) : value >> 1;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Geometry tuyến Google không hợp lệ.");
    }

    private record Value(long value, int nextIndex) {}
}
