package com.quangkhai.vehicletracking_backend.simulation.motion;

import com.quangkhai.vehicletracking_backend.route.entity.PolylineEncoding;

import java.util.List;

public final class RoutePolylineCodec {
    private RoutePolylineCodec() {}

    public static List<FlexiblePolyline.Point> decode(String encoded, PolylineEncoding encoding) {
        PolylineEncoding resolved = encoding == null ? PolylineEncoding.HERE_FLEXIBLE_POLYLINE : encoding;
        return switch (resolved) {
            case HERE_FLEXIBLE_POLYLINE -> FlexiblePolyline.decode(encoded);
            case GOOGLE_ENCODED_POLYLINE -> GooglePolyline.decode(encoded);
        };
    }
}
