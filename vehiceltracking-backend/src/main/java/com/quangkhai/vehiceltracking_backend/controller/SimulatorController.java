package com.quangkhai.vehiceltracking_backend.controller;

import com.quangkhai.vehiceltracking_backend.service.SimulatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/simulator")
@RequiredArgsConstructor
public class SimulatorController {

    private final SimulatorService simulatorService;

    @PostMapping("/start/{tripId}")
    public ResponseEntity<Map<String, Object>> startSimulation(@PathVariable Long tripId) {
        simulatorService.startSimulation(tripId);
        return ResponseEntity.ok(Map.of("message", "Simulator đã bắt đầu cho chuyến đi: " + tripId, "status", "RUNNING"));
    }

    @PostMapping("/pause/{tripId}")
    public ResponseEntity<Map<String, Object>> pauseSimulation(@PathVariable Long tripId) {
        simulatorService.pauseSimulation(tripId);
        return ResponseEntity.ok(Map.of("message", "Đã tạm dừng mô phỏng", "status", "PAUSED"));
    }

    @PostMapping("/resume/{tripId}")
    public ResponseEntity<Map<String, Object>> resumeSimulation(@PathVariable Long tripId) {
        simulatorService.resumeSimulation(tripId);
        return ResponseEntity.ok(Map.of("message", "Đã tiếp tục mô phỏng", "status", "RUNNING"));
    }

    @PostMapping("/reset/{tripId}")
    public ResponseEntity<Map<String, Object>> resetSimulation(@PathVariable Long tripId) {
        simulatorService.resetSimulation(tripId);
        return ResponseEntity.ok(Map.of("message", "Đã đặt lại trạng thái ban đầu", "status", "RESET"));
    }

    @PostMapping("/multiplier/{tripId}")
    public ResponseEntity<Map<String, Object>> setMultiplier(@PathVariable Long tripId, @RequestParam double multiplier) {
        simulatorService.setSpeedMultiplier(tripId, multiplier);
        return ResponseEntity.ok(Map.of("message", "Đã cập nhật hệ số tốc độ", "multiplier", multiplier));
    }

    @GetMapping("/status/{tripId}")
    public ResponseEntity<Object> getStatus(@PathVariable Long tripId) {
        SimulatorService.SimulationSession session = simulatorService.getSession(tripId);
        if (session == null) {
            return ResponseEntity.ok(Map.of("status", "IDLE"));
        }
        return ResponseEntity.ok(session);
    }
}
