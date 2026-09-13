package com.quangkhai.vehicletracking_backend.simulation;
import com.quangkhai.vehicletracking_backend.route.entity.*;
import com.quangkhai.vehicletracking_backend.station.entity.StationEntity;
import java.time.Instant;
public final class SimulationFixtures {
    private SimulationFixtures() {}
    public static String encode(double[][] points) {
        var output=new StringBuilder("BF"); long lat=0,lng=0;
        for(var point:points) {
            long a=Math.round(point[0]*100000),b=Math.round(point[1]*100000);
            append(output,(a-lat)<<1 ^ (a-lat)>>63); append(output,(b-lng)<<1 ^ (b-lng)>>63); lat=a;lng=b;
        } return output.toString();
    }
    private static void append(StringBuilder out,long value) {
        String alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        while(value>=32) { out.append(alphabet.charAt((int)(value&31)|32)); value>>>=5; } out.append(alphabet.charAt((int)value));
    }
    public static RouteEntity route(StationEntity a,StationEntity b) {
        var now=Instant.now();
        var route=new RouteEntity("SIMULATOR · Tuyến vòng A-B-A",RouteTransportMode.CAR,RoutingProviderName.HERE,
            312L,40L,40L,4L,44L,now,now);
        route.addStop(new RouteStopEntity(a,1,a.getName(),a.getLatitude(),a.getLongitude(),0));
        route.addStop(new RouteStopEntity(b,2,b.getName(),b.getLatitude(),b.getLongitude(),4));
        route.addStop(new RouteStopEntity(a,3,a.getName(),a.getLatitude(),a.getLongitude(),0));
        route.addSection(new RouteSectionEntity(1,2,encode(new double[][]{{10.77,106.70},{10.7702,106.7002},{10.771,106.701}}),156L,20L,20L));
        route.addSection(new RouteSectionEntity(2,3,encode(new double[][]{{10.771,106.701},{10.77,106.70}}),156L,20L,20L));
        return route;
    }
}
