package com.quangkhai.vehicletracking_backend.simulation.motion;
import java.util.*;

public final class FlexiblePolyline {
    public record Point(double latitude,double longitude) {}
    private static final String ALPHABET="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    private FlexiblePolyline() {}
    /** Encodes persisted route-prefix cuts with the same precision as HERE fixtures. */
    public static String encode(List<Point> points) {
        if (points==null || points.isEmpty()) throw invalid();
        var out=new StringBuilder("BF");long lat=0,lng=0;
        for(var p:points) {
            long a=Math.round(p.latitude()*100000),b=Math.round(p.longitude()*100000);
            append(out,(a-lat)<<1 ^ (a-lat)>>63);append(out,(b-lng)<<1 ^ (b-lng)>>63);lat=a;lng=b;
        }
        return out.toString();
    }
    private static void append(StringBuilder out,long value) {
        while(value>=32) { out.append(ALPHABET.charAt((int)(value&31)|32));value>>>=5; }
        out.append(ALPHABET.charAt((int)value));
    }
    public static List<Point> decode(String encoded) {
        if (encoded==null || encoded.isBlank() || encoded.length()>2_000_000) throw invalid();
        var reader=new Reader(encoded);
        if (reader.read()!=1) throw invalid();
        long header=reader.read();
        if (header>2047) throw invalid();
        double factor=Math.pow(10,header & 15);
        int dimension=(int)(header>>4)&7;
        long lat=0,lng=0;
        var points=new ArrayList<Point>();
        while(reader.index<encoded.length()) {
            lat=Math.addExact(lat,reader.signed()); lng=Math.addExact(lng,reader.signed());
            if(dimension!=0) reader.signed();
            var point=new Point(lat/factor,lng/factor);
            if (!Double.isFinite(point.latitude()) || Math.abs(point.latitude())>90 || Math.abs(point.longitude())>180) throw invalid();
            points.add(point);
        }
        if (points.isEmpty()) throw invalid();
        return List.copyOf(points);
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Geometry tuyến không hợp lệ."); }
    private static class Reader {
        final String value; int index;
        Reader(String value) { this.value=value; }
        long read() {
            long result=0;
            for(int shift=0;shift<=60;shift+=5) {
                if(index>=value.length()) throw invalid();
                int n=ALPHABET.indexOf(value.charAt(index++));
                if(n<0 || (shift==60 && (n&31)>7)) throw invalid();
                result|=(long)(n&31)<<shift;
                if((n&32)==0) return result;
            }
            throw invalid();
        }
        long signed() { long value=read(); return (value>>>1)^-(value&1); }
    }
}
