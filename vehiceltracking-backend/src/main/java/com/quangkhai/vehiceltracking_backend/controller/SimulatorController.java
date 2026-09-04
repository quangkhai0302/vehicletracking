package com.quangkhai.vehiceltracking_backend.controller;

import com.quangkhai.vehiceltracking_backend.dto.SimulatorResponseDto;
import com.quangkhai.vehiceltracking_backend.service.SimulatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/simulator")
@RequiredArgsConstructor
public class SimulatorController {

    private final SimulatorService simulatorService;

    @PostMapping("/start/{tripId}")
    public ResponseEntity<SimulatorResponseDto> startSimulation(@PathVariable Long tripId) {
        return ResponseEntity.ok(simulatorService.startSimulation(tripId));
    }

    @PostMapping("/pause/{tripId}")
    public ResponseEntity<SimulatorResponseDto> pauseSimulation(@PathVariable Long tripId) {
        return ResponseEntity.ok(simulatorService.pauseSimulation(tripId));
    }

    @PostMapping("/resume/{tripId}")
    public ResponseEntity<SimulatorResponseDto> resumeSimulation(@PathVariable Long tripId) {
        return ResponseEntity.ok(simulatorService.resumeSimulation(tripId));
    }

    @PostMapping("/reset/{tripId}")
    public ResponseEntity<SimulatorResponseDto> resetSimulation(@PathVariable Long tripId) {
        return ResponseEntity.ok(simulatorService.resetSimulation(tripId));
    }

    @PostMapping("/multiplier/{tripId}")
    public ResponseEntity<SimulatorResponseDto> setMultiplier(@PathVariable Long tripId, @RequestParam double multiplier) {
        return ResponseEntity.ok(simulatorService.setSpeedMultiplier(tripId, multiplier));
    }

    @GetMapping("/status/{tripId}")
    public ResponseEntity<SimulatorResponseDto> getStatus(@PathVariable Long tripId) {
        return ResponseEntity.ok(simulatorService.getStatus(tripId));
    }
}
