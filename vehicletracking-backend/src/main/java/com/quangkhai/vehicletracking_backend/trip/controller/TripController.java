package com.quangkhai.vehicletracking_backend.trip.controller;

import com.quangkhai.vehicletracking_backend.driver.dto.DriverAssignmentRequest;
import com.quangkhai.vehicletracking_backend.trip.dto.*;
import com.quangkhai.vehicletracking_backend.trip.service.TripService;
import com.quangkhai.vehicletracking_backend.vehicle.dto.VehicleAssignmentRequest;
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
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id); return ResponseEntity.noContent().build();
    }
    @PutMapping("/{id}/driver") public TripDetailResponse assignDriver(@PathVariable long id,
            @Valid @RequestBody DriverAssignmentRequest request) {
        return service.assignDriver(id, request.driverId());
    }
    @DeleteMapping("/{id}/driver") public ResponseEntity<Void> unassignDriver(@PathVariable long id) {
        service.unassignDriver(id); return ResponseEntity.noContent().build();
    }
    @PutMapping("/{id}/vehicle") public TripDetailResponse assignVehicle(@PathVariable long id,
            @Valid @RequestBody VehicleAssignmentRequest request) {
        return service.assignVehicle(id, request.vehicleId());
    }
    @PostMapping("/{id}/start") public TripDetailResponse start(@PathVariable long id) { return service.start(id); }
    @PostMapping("/{id}/complete") public TripDetailResponse complete(@PathVariable long id) { return service.complete(id); }
    @PostMapping("/{id}/cancel") public TripDetailResponse cancel(@PathVariable long id,
            @Valid @RequestBody CancelTripRequest request) { return service.cancel(id, request.reason()); }
    /** Compatibility overload for internal callers/tests; HTTP clients must send a reason body. */
    public TripDetailResponse cancel(long id) { return service.cancel(id); }
}
