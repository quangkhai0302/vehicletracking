package com.quangkhai.vehicletracking_backend.checkin.geometry;

import java.math.BigDecimal;

/** Small-circle checks in metres. The local projection is accurate for the short
 * GPS segments accepted by the detector and avoids any dependency on PostGIS. */
public final class GeofenceCrossing {
    public static final double EARTH_RADIUS_METERS = 6_371_000d;
    private GeofenceCrossing() {}
    public record Point(double latitude, double longitude) {}
    public record Crossing(double fraction, double latitude, double longitude) {}

    public static double distance(double latitude, double longitude, BigDecimal targetLatitude, BigDecimal targetLongitude) {
        return distance(new Point(latitude, longitude), new Point(targetLatitude.doubleValue(), targetLongitude.doubleValue()));
    }
    public static double distance(Point a, Point b) {
        if (a == null || b == null || !finite(a.latitude()) || !finite(a.longitude())
                || !finite(b.latitude()) || !finite(b.longitude())) return Double.NaN;
        double lat1=Math.toRadians(a.latitude()), lat2=Math.toRadians(b.latitude());
        double dLat=lat2-Math.toRadians(a.latitude()), dLon=Math.toRadians(normalizeDeltaLongitude(b.longitude()-a.longitude()));
        double h=Math.pow(Math.sin(dLat/2),2)+Math.cos(lat1)*Math.cos(lat2)*Math.pow(Math.sin(dLon/2),2);
        return EARTH_RADIUS_METERS*2*Math.asin(Math.sqrt(Math.min(1d,Math.max(0d,h))));
    }
    public static boolean inside(Point point, double latitude, double longitude, double radiusMeters) {
        return finite(radiusMeters) && radiusMeters >= 0 && distance(point, new Point(latitude,longitude)) <= radiusMeters + 0.01d;
    }
    /** Returns the first entry into a radius along the short local segment. */
    public static Crossing firstEntry(Point from, Point to, double centerLatitude, double centerLongitude, double radiusMeters) {
        if (from == null || to == null || !finite(centerLatitude) || !finite(centerLongitude)
                || !finite(radiusMeters) || radiusMeters < 0) return null;
        double scaleLat=EARTH_RADIUS_METERS*Math.PI/180d;
        double scaleLon=scaleLat*Math.cos(Math.toRadians(centerLatitude));
        double ax=normalizeDeltaLongitude(from.longitude()-centerLongitude)*scaleLon, ay=(from.latitude()-centerLatitude)*scaleLat;
        double bx=normalizeDeltaLongitude(to.longitude()-centerLongitude)*scaleLon, by=(to.latitude()-centerLatitude)*scaleLat;
        double dx=bx-ax,dy=by-ay, aa=dx*dx+dy*dy;
        double r2=radiusMeters*radiusMeters;
        if (aa <= 1e-9) return inside(from,centerLatitude,centerLongitude,radiusMeters) ? new Crossing(0,from.latitude(),from.longitude()) : null;
        double c=ax*ax+ay*ay-r2, b=2*(ax*dx+ay*dy), discriminant=b*b-4*aa*c;
        if (discriminant < 0) return null;
        double root=Math.sqrt(Math.max(0,discriminant));
        double t1=(-b-root)/(2*aa), t2=(-b+root)/(2*aa), t=Double.POSITIVE_INFINITY;
        if(t1>=0 && t1<=1) t=t1; else if(t2>=0 && t2<=1) t=t2;
        if(!Double.isFinite(t)) return inside(to,centerLatitude,centerLongitude,radiusMeters) ? new Crossing(1,to.latitude(),to.longitude()) : null;
        double deltaLon=normalizeDeltaLongitude(to.longitude()-from.longitude());
        return new Crossing(t,from.latitude()+(to.latitude()-from.latitude())*t,normalizeLongitude(from.longitude()+deltaLon*t));
    }
    private static boolean finite(double value) { return Double.isFinite(value); }
    private static double normalizeDeltaLongitude(double value) {
        return ((value + 540d) % 360d) - 180d;
    }
    private static double normalizeLongitude(double value) {
        return ((value + 540d) % 360d) - 180d;
    }
}
