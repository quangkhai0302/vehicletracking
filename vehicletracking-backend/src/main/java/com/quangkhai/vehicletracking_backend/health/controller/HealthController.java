package com.quangkhai.vehicletracking_backend.health.controller;

import com.quangkhai.vehicletracking_backend.health.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    private static final HealthResponse UP = new HealthResponse("UP");

    @GetMapping
    public HealthResponse health() {
        return UP;
    }
}
