package com.quangkhai.vehicletracking_backend.vehicle.controller;

import com.quangkhai.vehicletracking_backend.vehicle.dto.*;
import com.quangkhai.vehicletracking_backend.vehicle.service.VehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
public class VehicleController {
    private final VehicleService service;
    @GetMapping public List<VehicleResponse> findAll() { return service.findAll(); }
    @GetMapping("/{id}") public VehicleResponse findById(@PathVariable long id) { return service.findById(id); }
    @PostMapping public ResponseEntity<VehicleResponse> create(@Valid @RequestBody VehicleUpsertRequest request) {
        VehicleResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/vehicles/" + created.id())).body(created);
    }
    @PutMapping("/{id}") public VehicleResponse update(@PathVariable long id, @Valid @RequestBody VehicleUpsertRequest request) {
        return service.update(id, request);
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> deactivate(@PathVariable long id) {
        service.deactivate(id); return ResponseEntity.noContent().build();
    }
}
