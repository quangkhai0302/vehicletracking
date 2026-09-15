package com.quangkhai.vehicletracking_backend.simulation.motion;

import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
import com.quangkhai.vehicletracking_backend.checkin.geometry.GeofenceCrossing;
import com.quangkhai.vehicletracking_backend.simulation.motion.FlexiblePolyline.Point;
import java.util.*;

public final class RouteMotion {
    public record Frame(double latitude,double longitude,double heading,double speedKmh,double progressPercent,
            int nextStopSequence,double nextStopEtaSeconds,boolean dwelling,boolean finished) {}
    private record Leg(double start,double end,int destination,List<Point> points,double[] distances,double length,double distanceBefore) {}
    private record AppendResult(double elapsed,double distance,Point lastPoint) {}
    private final List<Leg> legs=new ArrayList<>();
    private final RouteDetailResponse route;
    /**
     * Simulator time is based on free-flow geometry.  The route snapshot may
     * already contain HERE's traffic-aware duration; using that value here
     * and applying live traffic again would slow the vehicle twice.
     */
    private final Map<Integer,Double> arrivalOffsets=new HashMap<>();
    private final Map<Integer,Double> departureOffsets=new HashMap<>();
    private double simulationDuration;
    private double totalDistance;
    public RouteMotion(RouteDetailResponse route) {
        this.route=route;
        double elapsed=0;
        if (!route.stops().isEmpty()) {
            arrivalOffsets.put(route.stops().getFirst().sequenceNumber(), 0d);
            departureOffsets.put(route.stops().getFirst().sequenceNumber(), 0d);
        }
        int sectionIndex=0;
        Point previous=null;
        for(int stop=1;stop<route.stops().size();stop++) {
            int destination=route.stops().get(stop).sequenceNumber();
            int count=0;
            while(sectionIndex<route.sections().size() && route.sections().get(sectionIndex).destinationStopSequence()==destination) {
                var section=route.sections().get(sectionIndex++); count++;
                var points=RoutePolylineCodec.decode(section.encodedPolyline(),section.polylineEncoding());
                if(previous!=null && distance(previous,points.getFirst())>100) throw invalid();
                double[] distances=new double[points.size()];
                for(int i=1;i<points.size();i++) distances[i]=distances[i-1]+distance(points.get(i-1),points.get(i));
                double length=distances[distances.length-1];
                double duration=motionDuration(section);
                if(duration<0 || (length>0 && duration==0) || (section.distanceMeters()>0 && length==0)
                    || (duration>0 && length/duration*3.6>500)) throw invalid();
                var appended=appendSection(legs,section,points,elapsed,totalDistance,duration);
                totalDistance=appended.distance();elapsed=appended.elapsed();previous=appended.lastPoint();
            }
            if(count==0) throw invalid();
            var stopDefinition=route.stops().get(stop);
            arrivalOffsets.put(destination, elapsed);
            elapsed+=stopDefinition.dwellDurationSeconds();
            departureOffsets.put(destination, elapsed);
        }
        if(legs.isEmpty() || totalDistance<=0 || sectionIndex!=route.sections().size()
            || !Double.isFinite(elapsed)) throw invalid();
        simulationDuration=elapsed;
    }
    private static double freeFlowDuration(RouteDetailResponse.RouteSectionResponse section) {
        // Routes created before baseDuration was persisted can have zero here;
        // retain their original snapshot duration instead of making geometry
        // impossible to simulate.
        long base=section.baseTravelDurationSeconds();
        if (base<=0 && section.travelDurationSeconds()>0) base=section.travelDurationSeconds();
        return base;
    }
    private static double motionDuration(RouteDetailResponse.RouteSectionResponse section) {
        if (section.polylineEncoding()==com.quangkhai.vehicletracking_backend.route.entity.PolylineEncoding.GOOGLE_ENCODED_POLYLINE
                && section.travelDurationSeconds()>0) return section.travelDurationSeconds();
        return freeFlowDuration(section);
    }

    /** Distributes Google's leg duration over its traffic categories without changing the total leg duration. */
    private static AppendResult appendSection(List<Leg> target,RouteDetailResponse.RouteSectionResponse section,
                                               List<Point> points,double elapsed,double travelled,double duration) {
        double[] cumulative=distances(points);double length=cumulative[cumulative.length-1];
        var intervals=section.trafficIntervals();
        if (section.polylineEncoding()!=com.quangkhai.vehicletracking_backend.route.entity.PolylineEncoding.GOOGLE_ENCODED_POLYLINE
                || intervals==null || intervals.isEmpty() || points.size()<2) {
            target.add(new Leg(elapsed,elapsed+duration,section.destinationStopSequence(),points,cumulative,length,travelled));
            return new AppendResult(elapsed+duration,travelled+length,points.getLast());
        }
        double[] weights=new double[points.size()-1];Arrays.fill(weights,1d);
        for(var interval:intervals) {
            double weight=switch(interval.category()) {
                case NORMAL -> 1d; case SLOW -> 2d; case TRAFFIC_JAM -> 4d; case UNKNOWN -> 1d;
            };
            int start=Math.max(0,interval.startPolylinePointIndex());
            int end=Math.min(weights.length,interval.endPolylinePointIndex());
            for(int segment=start;segment<end;segment++) weights[segment]=weight;
        }
        double weightedTotal=0;
        for(int i=0;i<weights.length;i++) weightedTotal+=(cumulative[i+1]-cumulative[i])*weights[i];
        if (!(weightedTotal>0) || !Double.isFinite(weightedTotal)) {
            target.add(new Leg(elapsed,elapsed+duration,section.destinationStopSequence(),points,cumulative,length,travelled));
            return new AppendResult(elapsed+duration,travelled+length,points.getLast());
        }
        int groupStart=0;
        for(int segment=1;segment<=weights.length;segment++) {
            if(segment<weights.length && weights[segment]==weights[groupStart]) continue;
            List<Point> group=List.copyOf(points.subList(groupStart,segment+1));
            double[] groupDistances=distances(group);double groupLength=groupDistances[groupDistances.length-1];
            double groupDuration=duration*(groupLength*weights[groupStart])/weightedTotal;
            target.add(new Leg(elapsed,elapsed+groupDuration,section.destinationStopSequence(),group,groupDistances,groupLength,travelled));
            elapsed+=groupDuration;travelled+=groupLength;groupStart=segment;
        }
        return new AppendResult(elapsed,travelled,points.getLast());
    }
    public double duration() { return simulationDuration; }

    /** Retains the travelled prefix, then appends the replacement from the current position. */
    public void revise(List<RouteDetailResponse.RouteSectionResponse> sections,double atElapsed) {
        if (sections.isEmpty() || !Double.isFinite(atElapsed) || atElapsed<0 || atElapsed>=duration()) throw invalid();
        var current=at(atElapsed);
        if (current.dwelling()) throw invalid();
        var expected=route.stops().stream().map(RouteDetailResponse.RouteStopResponse::sequenceNumber)
            .filter(sequence -> sequence>=current.nextStopSequence()).toList();
        List<Integer> destinations=new ArrayList<>();
        for(var section:sections) {
            if(destinations.isEmpty() || destinations.getLast()!=section.destinationStopSequence())
                destinations.add(section.destinationStopSequence());
        }
        if (!destinations.equals(expected)) throw invalid();
        var first=RoutePolylineCodec.decode(sections.getFirst().encodedPolyline(),sections.getFirst().polylineEncoding()).getFirst();
        if (distance(new Point(current.latitude(),current.longitude()),first)>100) throw invalid();
        List<Leg> replacement=new ArrayList<>();
        double travelled=0;
        for (var leg:legs) {
            if (leg.start()>=atElapsed) break;
            if (leg.end()<=atElapsed) { replacement.add(leg);travelled+=leg.length();continue; }
            var points=new ArrayList<Point>();points.add(leg.points().getFirst());
            double length=leg.length()*(atElapsed-leg.start())/(leg.end()-leg.start());
            for(int i=1;i<leg.points().size();i++) if(leg.distances()[i]<length) points.add(leg.points().get(i));
            points.add(pointAt(leg,atElapsed));
            double[] distances=distances(points);
            replacement.add(new Leg(leg.start(),atElapsed,leg.destination(),points,distances,length,travelled));
            travelled+=length;
        }
        double elapsed=atElapsed;
        var arrivals=new HashMap<>(arrivalOffsets);var departures=new HashMap<>(departureOffsets);
        for(int index=0;index<sections.size();index++) {
            var section=sections.get(index);var points=RoutePolylineCodec.decode(section.encodedPolyline(),section.polylineEncoding());
            if (index>0 && distance(replacement.getLast().points().getLast(),points.getFirst())>100) throw invalid();
            double[] distances=distances(points);double length=distances[distances.length-1];
            double duration=motionDuration(section);
            if(duration<0 || (length>0 && duration==0) || (duration>0 && length/duration*3.6>500)) throw invalid();
            var appended=appendSection(replacement,section,points,elapsed,travelled,duration);
            travelled=appended.distance();elapsed=appended.elapsed();
            if(index==sections.size()-1 || sections.get(index+1).destinationStopSequence()!=section.destinationStopSequence()) {
                var stop=route.stops().stream().filter(s->s.sequenceNumber()==section.destinationStopSequence()).findFirst().orElseThrow(RouteMotion::invalid);
                arrivals.put(stop.sequenceNumber(),elapsed);elapsed+=stop.dwellDurationSeconds();departures.put(stop.sequenceNumber(),elapsed);
            }
        }
        if (!Double.isFinite(elapsed) || travelled<=0) throw invalid();
        legs.clear();legs.addAll(replacement);totalDistance=travelled;simulationDuration=elapsed;
        arrivalOffsets.clear();arrivalOffsets.putAll(arrivals);departureOffsets.clear();departureOffsets.putAll(departures);
    }

    private static double[] distances(List<Point> points) {
        var result=new double[points.size()];
        for(int i=1;i<points.size();i++) result[i]=result[i-1]+distance(points.get(i-1),points.get(i));
        return result;
    }

    public RouteDetailResponse snapshot() {
        List<RouteDetailResponse.RouteSectionResponse> sections=new ArrayList<>();
        for(var leg:legs) sections.add(new RouteDetailResponse.RouteSectionResponse(sections.size()+1,leg.destination(),
            FlexiblePolyline.encode(leg.points()),Math.round(leg.length()),Math.max(1,Math.round(leg.end()-leg.start())),Math.max(1,Math.round(leg.end()-leg.start()))));
        var stops=route.stops().stream().map(s -> new RouteDetailResponse.RouteStopResponse(s.sequenceNumber(),s.role(),s.stationId(),s.stationName(),
            s.latitude(),s.longitude(),s.dwellDurationSeconds(),
            sections.stream().filter(section -> section.destinationStopSequence()==s.sequenceNumber()).mapToLong(RouteDetailResponse.RouteSectionResponse::distanceMeters).sum(),
            sections.stream().filter(section -> section.destinationStopSequence()==s.sequenceNumber()).mapToLong(RouteDetailResponse.RouteSectionResponse::travelDurationSeconds).sum(),
            Math.round(arrivalOffsets.get(s.sequenceNumber())),Math.round(departureOffsets.get(s.sequenceNumber())))).toList();
        long travel=sections.stream().mapToLong(RouteDetailResponse.RouteSectionResponse::baseTravelDurationSeconds).sum();
        return new RouteDetailResponse(route.id(),route.name(),route.transportMode(),route.routingProvider(),Math.round(totalDistance),travel,travel,
            route.totalDwellDurationSeconds(),Math.round(duration()),route.estimatedDepartureAt(),route.calculatedAt(),route.createdAt(),
            route.geometryVersion(),route.providerContentExpiresAt(),stops,sections,route.shapingPoints());
    }

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
                    leg.destination(),Math.max(0,arrivalOffsets.getOrDefault(leg.destination(), elapsed)-elapsed),false,false);
            }
            // A dwell belongs to the final section of this stop, not intermediate sections.
            var stop=route.stops().stream().filter(item -> item.sequenceNumber()==leg.destination()).findFirst().orElseThrow();
            double arrival=arrivalOffsets.getOrDefault(stop.sequenceNumber(), leg.end());
            double departure=departureOffsets.getOrDefault(stop.sequenceNumber(), arrival+stop.dwellDurationSeconds());
            if(elapsed>=arrival && elapsed<departure && Math.abs(leg.end()-arrival)<1e-9) {
                var p=leg.points().getLast();
                int next=Math.min(stop.sequenceNumber()+1,route.stops().size());
                return new Frame(p.latitude(),p.longitude(),0,0,(leg.distanceBefore()+leg.length())/totalDistance*100,next,
                    Math.max(0,arrivalOffsets.getOrDefault(next, elapsed)-elapsed),true,false);
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
