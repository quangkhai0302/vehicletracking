package com.quangkhai.vehicletracking_backend.reroute.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.quangkhai.vehicletracking_backend.reroute.dto.RouteComparisonResponse;
import com.quangkhai.vehicletracking_backend.reroute.service.RouteComparisonService;

import lombok.RequiredArgsConstructor;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/trips/{tripId}/revisions/{revisionId}/comparison")
public class RouteComparisonController {
    private final RouteComparisonService comparisons;
    @GetMapping public RouteComparisonResponse find(@PathVariable long tripId, @PathVariable long revisionId) {
        return comparisons.find(tripId, revisionId);
    }
}
