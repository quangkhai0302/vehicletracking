package com.quangkhai.vehicletracking_backend.trip;

import com.quangkhai.vehicletracking_backend.route.entity.*;
import com.quangkhai.vehicletracking_backend.station.entity.StationEntity;
import java.math.BigDecimal;
import java.time.Instant;

public final class TripFixtures {
    private TripFixtures() {}
    public static StationEntity station(String name) {
        return new StationEntity(name, null, new BigDecimal("10.772300"), new BigDecimal("106.698100"), 50);
    }
    public static RouteEntity route(StationEntity a, StationEntity b) {
        var route = new RouteEntity("Tuyến vòng A-B-A", RouteTransportMode.CAR, RoutingProviderName.HERE,
                2400L, 600L, 60L, 60L, 660L, Instant.parse("2026-09-13T16:00:00Z"), Instant.parse("2026-09-13T16:00:00Z"));
        route.addStop(new RouteStopEntity(a, 1, a.getName(), a.getLatitude(), a.getLongitude(), 0));
        route.addStop(new RouteStopEntity(b, 2, b.getName(), b.getLatitude(), b.getLongitude(), 60));
        route.addStop(new RouteStopEntity(a, 3, a.getName(), a.getLatitude(), a.getLongitude(), 0));
        route.addSection(new RouteSectionEntity(1, 2, "BFoz5xJ67i0xG", 1200L, 300L, 30L));
        route.addSection(new RouteSectionEntity(2, 3, "BFoz5xJ67i0xG", 1200L, 300L, 30L));
        return route;
    }
}
