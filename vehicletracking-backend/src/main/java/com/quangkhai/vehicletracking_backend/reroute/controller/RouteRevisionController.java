package com.quangkhai.vehicletracking_backend.reroute.controller;

import com.quangkhai.vehicletracking_backend.reroute.dto.RouteRevisionResponse;
import com.quangkhai.vehicletracking_backend.reroute.service.RouteRevisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/revisions")
@RequiredArgsConstructor
public class RouteRevisionController {
    private final RouteRevisionService revisions;
    @GetMapping public List<RouteRevisionResponse> list(@PathVariable long tripId) { return revisions.findByTrip(tripId); }
    @PostMapping("/{revisionId}/supersede")
    public RouteRevisionResponse supersede(@PathVariable long tripId, @PathVariable long revisionId) {
        return revisions.supersede(tripId, revisionId);
    }
}
