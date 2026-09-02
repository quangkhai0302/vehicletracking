package com.quangkhai.vehiceltracking_backend.controller;

import com.quangkhai.vehiceltracking_backend.dto.StationDto;
import com.quangkhai.vehiceltracking_backend.service.StationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stations")
@RequiredArgsConstructor
public class StationController {

    private final StationService stationService;

    @GetMapping
    public ResponseEntity<List<StationDto>> getAllStations() {
        return ResponseEntity.ok(stationService.getAllStations());
    }

    @GetMapping("/{id}")
    public ResponseEntity<StationDto> getStationById(@PathVariable Long id) {
        return ResponseEntity.ok(stationService.getStationById(id));
    }

    @PostMapping
    public ResponseEntity<StationDto> createStation(@Valid @RequestBody StationDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stationService.createStation(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<StationDto> updateStation(@PathVariable Long id, @Valid @RequestBody StationDto dto) {
        return ResponseEntity.ok(stationService.updateStation(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStation(@PathVariable Long id) {
        stationService.deleteStation(id);
        return ResponseEntity.noContent().build();
    }
}
