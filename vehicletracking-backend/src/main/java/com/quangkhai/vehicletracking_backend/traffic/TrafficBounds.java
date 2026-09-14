package com.quangkhai.vehicletracking_backend.traffic;

import java.util.Locale;

public record TrafficBounds(double west, double south, double east, double north) {
    public static TrafficBounds of(double west, double south, double east, double north, int maxSpanHundredths) {
        if (!Double.isFinite(west) || !Double.isFinite(south) || !Double.isFinite(east) || !Double.isFinite(north)
                || west < -180 || west > 180 || east < -180 || east > 180
                || south < -90 || south > 90 || north < -90 || north > 90
                || west >= east || south >= north
                || (east - west) * 100 > maxSpanHundredths || (north - south) * 100 > maxSpanHundredths) {
            throw new TrafficOperationException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    TrafficErrorCode.TRAFFIC_BOUNDS_INVALID,
                    "Traffic bounds must be finite, ordered and within the supported area"
            );
        }
        return new TrafficBounds(west, south, east, north);
    }

    public String cacheKey() {
        return String.format(Locale.ROOT, "%.6f,%.6f,%.6f,%.6f", west, south, east, north);
    }

    public String hereBbox() {
        return String.format(Locale.ROOT, "bbox:%.6f,%.6f,%.6f,%.6f", west, south, east, north);
    }
}
