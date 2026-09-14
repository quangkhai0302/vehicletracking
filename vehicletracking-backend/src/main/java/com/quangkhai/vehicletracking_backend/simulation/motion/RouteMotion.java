package com.quangkhai.vehicletracking_backend.simulation.motion;

import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
import com.quangkhai.vehicletracking_backend.checkin.geometry.GeofenceCrossing;
import com.quangkhai.vehicletracking_backend.simulation.motion.FlexiblePolyline.Point;
import java.util.*;

public final class RouteMotion {
    public record Frame(double latitude,double longitude,double heading,double speedKmh,double progressPercent,
            int nextStopSequence,double nextStopEtaSeconds,boolean dwelling,boolean finished) {}
    private record Leg(double start,double end,int destination,List<Point> points,double[] distances,double length,double distanceBefore) {}
    private final List<Leg> legs=new ArrayList<>();
    private final RouteDetailResponse route;
    private double totalDistance;
    public RouteMotion(RouteDetailResponse route) {
        this.route=route;
        double elapsed=0;
        int sectionIndex=0;
        Point previous=null;
        for(int stop=1;stop<route.stops().size();stop++) {
            int destination=route.stops().get(stop).sequenceNumber();
            int count=0;
            while(sectionIndex<route.sections().size() && route.sections().get(sectionIndex).destinationStopSequence()==destination) {
                var section=route.sections().get(sectionIndex++); count++;
                var points=FlexiblePolyline.decode(section.encodedPolyline());
                if(previous!=null && distance(previous,points.getFirst())>100) throw invalid();
                double[] distances=new double[points.size()];
                for(int i=1;i<points.size();i++) distances[i]=distances[i-1]+distance(points.get(i-1),points.get(i));
                double length=distances[distances.length-1];
                double duration=section.travelDurationSeconds();
                if(duration<0 || (length>0 && duration==0) || (section.distanceMeters()>0 && length==0)
                    || (duration>0 && length/duration*3.6>500)) throw invalid();
                legs.add(new Leg(elapsed,elapsed+duration,destination,points,distances,length,totalDistance));
                totalDistance+=length; elapsed+=duration; previous=points.getLast();
            }
            if(count==0) throw invalid();
            elapsed+=route.stops().get(stop).dwellDurationSeconds();
        }
        if(legs.isEmpty() || totalDistance<=0 || sectionIndex!=route.sections().size()
            || elapsed!=route.estimatedTripDurationSeconds() || !Double.isFinite(elapsed)) throw invalid();
    }
    public double duration() { return route.estimatedTripDurationSeconds(); }

    /**
     * Finds the first outside-to-inside crossing of a stop along the actual
     * decoded route trace between two simulator elapsed times. Unlike a GPS
     * segment this walks every polyline vertex and never joins two sections
     * across a geometry gap.
     */
    public GeofenceCrossing.Crossing firstEntryBetween(double fromElapsed, double toElapsed,
                                                        double centerLatitude, double centerLongitude,
                                                        double radiusMeters, double minimumFraction) {
        if (!Double.isFinite(fromElapsed) || !Double.isFinite(toElapsed)
                || toElapsed < fromElapsed || !Double.isFinite(minimumFraction)) return null;
        double start = Math.max(0, Math.min(duration(), fromElapsed));
        double end = Math.max(0, Math.min(duration(), toElapsed));
        double span = end - start;
        if (span <= 0) {
            var point = at(start);
            return GeofenceCrossing.inside(new GeofenceCrossing.Point(point.latitude(), point.longitude()),
                    centerLatitude, centerLongitude, radiusMeters)
                    ? new GeofenceCrossing.Crossing(1, point.latitude(), point.longitude()) : null;
        }
        for (var leg : legs) {
            double legStart = Math.max(start, leg.start());
            double legEnd = Math.min(end, leg.end());
            if (legEnd <= legStart || leg.end() <= leg.start()) continue;
            var points = new ArrayList<TimedPoint>();
            points.add(new TimedPoint(legStart, pointAt(leg, legStart)));
            for (int i = 1; i < leg.points().size() - 1; i++) {
                if (leg.length() <= 0) continue;
                double vertexElapsed = leg.start() + leg.distances()[i] / leg.length() * (leg.end() - leg.start());
                if (vertexElapsed > legStart && vertexElapsed < legEnd)
                    points.add(new TimedPoint(vertexElapsed, leg.points().get(i)));
            }
            points.add(new TimedPoint(legEnd, pointAt(leg, legEnd)));
            for (int i = 1; i < points.size(); i++) {
                var a = points.get(i - 1);
                var b = points.get(i);
                var from = new GeofenceCrossing.Point(a.point().latitude(), a.point().longitude());
                var to = new GeofenceCrossing.Point(b.point().latitude(), b.point().longitude());
                if (GeofenceCrossing.inside(from, centerLatitude, centerLongitude, radiusMeters)) continue;
                var crossing = GeofenceCrossing.firstEntry(from, to, centerLatitude, centerLongitude, radiusMeters);
                if (crossing == null) continue;
                double globalFraction = (a.elapsed() + (b.elapsed() - a.elapsed()) * crossing.fraction() - start) / span;
                if (globalFraction > minimumFraction + 1e-9)
                    return new GeofenceCrossing.Crossing(globalFraction, crossing.latitude(), crossing.longitude());
            }
        }
        return null;
    }

    private record TimedPoint(double elapsed, Point point) {}

    private static Point pointAt(Leg leg, double elapsed) {
        if (leg.points().size() == 1 || leg.length() <= 0) return leg.points().getFirst();
        double fraction = (elapsed - leg.start()) / (leg.end() - leg.start());
        double target = Math.max(0, Math.min(leg.length(), fraction * leg.length()));
        int segment = 1;
        while (segment < leg.distances().length - 1 && leg.distances()[segment] <= target) segment++;
        var a = leg.points().get(segment - 1);
        var b = leg.points().get(Math.min(segment, leg.points().size() - 1));
        double segmentLength = leg.distances()[segment] - leg.distances()[segment - 1];
        double ratio = segmentLength <= 0 ? 0 : (target - leg.distances()[segment - 1]) / segmentLength;
        double deltaLon = ((b.longitude() - a.longitude() + 540) % 360) - 180;
        double lon = ((a.longitude() + deltaLon * ratio + 540) % 360) - 180;
        return new Point(a.latitude() + (b.latitude() - a.latitude()) * ratio, lon);
    }
    public Frame at(double elapsed) {
        if(!Double.isFinite(elapsed)) throw invalid();
        elapsed=Math.max(0,Math.min(duration(),elapsed));
        var last=legs.getLast();
        if(elapsed>=duration()) {
            var p=last.points().getLast();
            return new Frame(p.latitude(),p.longitude(),0,0,100,route.stops().size(),0,false,true);
        }
        for(var leg:legs) {
            if(elapsed<leg.end() && elapsed>=leg.start()) {
                double fraction=(elapsed-leg.start())/(leg.end()-leg.start());
                double target=leg.length()*fraction;
                int segment=1;
                while(segment<leg.distances().length-1 && leg.distances()[segment]<=target) segment++;
                var a=leg.points().get(Math.max(0,segment-1)); var b=leg.points().get(Math.min(segment,leg.points().size()-1));
                double segmentLength=leg.distances()[Math.min(segment,leg.distances().length-1)]-leg.distances()[Math.max(0,segment-1)];
                double ratio=segmentLength==0?0:(target-leg.distances()[segment-1])/segmentLength;
                double deltaLon=((b.longitude()-a.longitude()+540)%360)-180;
                double lon=((a.longitude()+deltaLon*ratio+540)%360)-180;
                return new Frame(a.latitude()+(b.latitude()-a.latitude())*ratio,lon,bearing(a,b),
                    leg.length()/(leg.end()-leg.start())*3.6,(leg.distanceBefore()+target)/totalDistance*100,
                    leg.destination(),Math.max(0,route.stops().get(leg.destination()-1).arrivalOffsetSeconds()-elapsed),false,false);
            }
            // A dwell belongs to the final section of this stop, not intermediate sections.
            var stop=route.stops().get(leg.destination()-1);
            if(elapsed>=stop.arrivalOffsetSeconds() && elapsed<stop.departureOffsetSeconds() && leg.end()==stop.arrivalOffsetSeconds()) {
                var p=leg.points().getLast();
                int next=Math.min(stop.sequenceNumber()+1,route.stops().size());
                return new Frame(p.latitude(),p.longitude(),0,0,(leg.distanceBefore()+leg.length())/totalDistance*100,next,
                    Math.max(0,route.stops().get(next-1).arrivalOffsetSeconds()-elapsed),true,false);
            }
        }
        throw invalid();
    }
    public static double distance(Point a,Point b) {
        double dLat=Math.toRadians(b.latitude()-a.latitude()),dLon=Math.toRadians(b.longitude()-a.longitude());
        double h=Math.pow(Math.sin(dLat/2),2)+Math.cos(Math.toRadians(a.latitude()))*Math.cos(Math.toRadians(b.latitude()))*Math.pow(Math.sin(dLon/2),2);
        return 6371000*2*Math.asin(Math.sqrt(Math.min(1,h)));
    }
    private static double bearing(Point a,Point b) {
        double dLon=Math.toRadians(b.longitude()-a.longitude());
        double y=Math.sin(dLon)*Math.cos(Math.toRadians(b.latitude()));
        double x=Math.cos(Math.toRadians(a.latitude()))*Math.sin(Math.toRadians(b.latitude()))-
            Math.sin(Math.toRadians(a.latitude()))*Math.cos(Math.toRadians(b.latitude()))*Math.cos(dLon);
        return (Math.toDegrees(Math.atan2(y,x))+360)%360;
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Geometry hoặc thời lượng tuyến không đủ để mô phỏng. Hãy tính lại tuyến."); }
}
