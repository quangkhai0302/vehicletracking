package com.quangkhai.vehiceltracking_backend.controller;

import com.quangkhai.vehiceltracking_backend.dto.RouteRequestDto;
import com.quangkhai.vehiceltracking_backend.dto.RouteResponseDto;
import com.quangkhai.vehiceltracking_backend.service.RouteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    @GetMapping
    public ResponseEntity<List<RouteResponseDto>> getAllRoutes() {
        return ResponseEntity.ok(routeService.getAllRoutes());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RouteResponseDto> getRouteById(@PathVariable Long id) {
        return ResponseEntity.ok(routeService.getRouteById(id));
    }

    @PostMapping
    public ResponseEntity<RouteResponseDto> createRoute(@Valid @RequestBody RouteRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(routeService.createRoute(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RouteResponseDto> updateRoute(@PathVariable Long id, @Valid @RequestBody RouteRequestDto request) {
        return ResponseEntity.ok(routeService.updateRoute(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRoute(@PathVariable Long id) {
        routeService.deleteRoute(id);
        return ResponseEntity.noContent().build();
    }
}
