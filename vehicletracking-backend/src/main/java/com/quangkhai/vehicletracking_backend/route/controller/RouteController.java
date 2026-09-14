package com.quangkhai.vehicletracking_backend.route.controller;

import com.quangkhai.vehicletracking_backend.route.dto.RouteCreateRequest;
import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
import com.quangkhai.vehicletracking_backend.route.dto.RouteSummaryResponse;
import com.quangkhai.vehicletracking_backend.route.service.RouteService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @PostMapping
    public ResponseEntity<RouteDetailResponse> create(@Valid @RequestBody RouteCreateRequest request) {
        RouteDetailResponse created = routeService.create(request);
        URI location = URI.create("/api/v1/routes/" + created.id());
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public List<RouteSummaryResponse> findAll() {
        return routeService.findAll();
    }

    @GetMapping("/{id}")
    public RouteDetailResponse findById(@PathVariable Long id) {
        return routeService.findById(id);
    }

    @PutMapping("/{id}")
    public RouteDetailResponse update(@PathVariable long id, @Valid @RequestBody RouteCreateRequest request) {
        return routeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable long id) {
        routeService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
