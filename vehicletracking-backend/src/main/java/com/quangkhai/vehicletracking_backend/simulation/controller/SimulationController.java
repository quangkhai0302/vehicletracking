package com.quangkhai.vehicletracking_backend.simulation.controller;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.quangkhai.vehicletracking_backend.simulation.dto.SimulationAttemptResponse;
import com.quangkhai.vehicletracking_backend.simulation.dto.SimulationResponse;
import com.quangkhai.vehicletracking_backend.simulation.dto.SimulationScenarioRequest;
import com.quangkhai.vehicletracking_backend.simulation.dto.SimulationSpeedRequest;
import com.quangkhai.vehicletracking_backend.simulation.service.SimulationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
@RestController @RequestMapping("/api/v1/trips/{tripId}/simulation") @RequiredArgsConstructor
public class SimulationController {
    private final SimulationService service;
    @PostMapping("/play") public SimulationResponse play(@PathVariable long tripId) { return service.play(tripId); }
    @PostMapping("/pause") public SimulationResponse pause(@PathVariable long tripId) { return service.pause(tripId); }
    @PostMapping("/speed") public SimulationResponse speed(@PathVariable long tripId,@Valid @RequestBody SimulationSpeedRequest body) { return service.speed(tripId,body.multiplier()); }
    @PostMapping("/scenario") public SimulationResponse scenario(@PathVariable long tripId,@Valid @RequestBody SimulationScenarioRequest body) { return service.scenario(tripId,body.scenario(),body.attemptNumber()); }
    @PostMapping("/stop") public SimulationResponse stop(@PathVariable long tripId) { return service.stop(tripId); }
    @PostMapping("/reset") public SimulationResponse reset(@PathVariable long tripId) { return service.reset(tripId); }
    @GetMapping("/attempts") public List<SimulationAttemptResponse> attempts(@PathVariable long tripId) { return service.attempts(tripId); }
}
