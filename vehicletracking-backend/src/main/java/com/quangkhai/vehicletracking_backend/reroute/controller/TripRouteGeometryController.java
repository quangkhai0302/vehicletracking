package com.quangkhai.vehicletracking_backend.reroute.controller;
import com.quangkhai.vehicletracking_backend.reroute.service.TripRouteGeometryQueryService;
import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class TripRouteGeometryController {
    private final TripRouteGeometryQueryService query;
    @GetMapping("/api/v1/trips/{id}/route") public RouteDetailResponse get(@PathVariable long id) { return query.get(id); }
}
