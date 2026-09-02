package com.quangkhai.vehiceltracking_backend.controller;

import com.quangkhai.vehiceltracking_backend.dto.TrafficIncidentDto;
import com.quangkhai.vehiceltracking_backend.service.TrafficIncidentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
public class TrafficIncidentController {

    private final TrafficIncidentService incidentService;

    @GetMapping
    public ResponseEntity<List<TrafficIncidentDto>> getAllIncidents() {
        return ResponseEntity.ok(incidentService.getAllIncidents());
    }

    @GetMapping("/active")
    public ResponseEntity<List<TrafficIncidentDto>> getActiveIncidents() {
        return ResponseEntity.ok(incidentService.getActiveIncidents());
    }

    @PostMapping
    public ResponseEntity<TrafficIncidentDto> createIncident(@Valid @RequestBody TrafficIncidentDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(incidentService.createIncident(dto));
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<TrafficIncidentDto> toggleIncident(@PathVariable Long id) {
        return ResponseEntity.ok(incidentService.toggleIncident(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteIncident(@PathVariable Long id) {
        incidentService.deleteIncident(id);
        return ResponseEntity.noContent().build();
    }
}
