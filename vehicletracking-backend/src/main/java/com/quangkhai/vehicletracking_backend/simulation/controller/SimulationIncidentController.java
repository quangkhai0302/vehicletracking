package com.quangkhai.vehicletracking_backend.simulation.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.quangkhai.vehicletracking_backend.simulation.dto.SimulationIncidentResponse;
import com.quangkhai.vehicletracking_backend.simulation.service.SimulationIncidentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/simulation-incidents")
@RequiredArgsConstructor
public class SimulationIncidentController {
    private final SimulationIncidentService incidents;

    @PostMapping("/{id}/acknowledge")
    public SimulationIncidentResponse acknowledge(@PathVariable long id) {
        return incidents.acknowledge(id);
    }

    @PostMapping("/{id}/resolve")
    public SimulationIncidentResponse resolve(@PathVariable long id) {
        return incidents.resolve(id);
    }
}
