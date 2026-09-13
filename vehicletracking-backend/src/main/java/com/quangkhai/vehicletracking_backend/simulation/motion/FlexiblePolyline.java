package com.quangkhai.vehicletracking_backend.simulation.motion;
import java.util.*;

public final class FlexiblePolyline {
    public record Point(double latitude,double longitude) {}
    private static final String ALPHABET="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    private FlexiblePolyline() {}
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
