package com.quangkhai.vehiceltracking_backend.controller;

import com.quangkhai.vehiceltracking_backend.dto.TripDto;
import com.quangkhai.vehiceltracking_backend.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @GetMapping
    public ResponseEntity<List<TripDto>> getAllTrips() {
        return ResponseEntity.ok(tripService.getAllTrips());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TripDto> getTripById(@PathVariable Long id) {
        return ResponseEntity.ok(tripService.getTripById(id));
    }

    @PostMapping
    public ResponseEntity<TripDto> createTrip(@RequestParam Long routeId, @RequestParam Long vehicleId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tripService.createTrip(routeId, vehicleId));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<Void> completeTrip(@PathVariable Long id) {
        tripService.completeTrip(id);
        return ResponseEntity.ok().build();
    }
}
