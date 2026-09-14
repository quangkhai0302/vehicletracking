package com.quangkhai.vehicletracking_backend.trip.controller;

import com.quangkhai.vehicletracking_backend.trip.dto.*;
import com.quangkhai.vehicletracking_backend.trip.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
public class TripController {
    private final TripService service;
    @GetMapping public List<TripSummaryResponse> findAll(@RequestParam(required = false) Long vehicleId) { return service.findAll(vehicleId); }
    @GetMapping("/{id}") public TripDetailResponse findById(@PathVariable long id) { return service.findById(id); }
    @PostMapping public ResponseEntity<TripDetailResponse> create(@Valid @RequestBody TripCreateRequest request) {
        var created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/trips/" + created.trip().id())).body(created);
    }
    @PutMapping("/{id}") public TripDetailResponse update(@PathVariable long id, @Valid @RequestBody TripUpdateRequest request) {
        return service.update(id, request);
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id); return ResponseEntity.noContent().build();
    }
    @PostMapping("/{id}/start") public TripDetailResponse start(@PathVariable long id) { return service.start(id); }
    @PostMapping("/{id}/complete") public TripDetailResponse complete(@PathVariable long id) { return service.complete(id); }
    @PostMapping("/{id}/cancel") public TripDetailResponse cancel(@PathVariable long id) { return service.cancel(id); }
}
