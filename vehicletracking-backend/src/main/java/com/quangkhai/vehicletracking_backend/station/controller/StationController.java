package com.quangkhai.vehicletracking_backend.station.controller;

import com.quangkhai.vehicletracking_backend.station.dto.StationResponse;
import com.quangkhai.vehicletracking_backend.station.dto.StationUpsertRequest;
import com.quangkhai.vehicletracking_backend.station.service.StationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/stations")
public class StationController {

    private final StationService stationService;

    public StationController(StationService stationService) {
        this.stationService = stationService;
    }

    @GetMapping
    public List<StationResponse> findAll() {
        return stationService.findAll();
    }

    @GetMapping("/{id}")
    public StationResponse findById(@PathVariable long id) {
        return stationService.findById(id);
    }

    @PostMapping
    public ResponseEntity<StationResponse> create(@Valid @RequestBody StationUpsertRequest request) {
        StationResponse created = stationService.create(request);
        return ResponseEntity
                .created(URI.create("/api/v1/stations/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    public StationResponse update(
            @PathVariable long id,
            @Valid @RequestBody StationUpsertRequest request
    ) {
        return stationService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        stationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
