package com.quangkhai.vehiceltracking_backend.util;

public class GeoUtil {

    private static final double EARTH_RADIUS_METERS = 6371000.0; // WGS-84 Earth radius

    /**
     * Tính khoảng cách giữa hai tọa độ địa lý bằng công thức Haversine (đơn vị: mét)
     */
    public static double calculateDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Tính khoảng cách theo Kilomet
     */
    public static double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        return calculateDistanceMeters(lat1, lon1, lat2, lon2) / 1000.0;
    }

    /**
     * Tính góc xoay (bearing/heading) từ điểm A đến điểm B (0 - 360 độ)
     */
    public static double calculateBearing(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaLambda = Math.toRadians(lon2 - lon1);

        double y = Math.sin(deltaLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(deltaLambda);

        double theta = Math.atan2(y, x);
        double bearing = (Math.toDegrees(theta) + 360.0) % 360.0;
        return bearing;
    }

    /**
     * Nội suy tọa độ giữa điểm 1 và điểm 2 theo tỷ lệ fraction (0.0 đến 1.0)
     */
    public static double[] interpolate(double lat1, double lon1, double lat2, double lon2, double fraction) {
        double lat = lat1 + (lat2 - lat1) * fraction;
        double lon = lon1 + (lon2 - lon1) * fraction;
        return new double[]{lat, lon};
    }
}
