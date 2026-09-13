package com.quangkhai.vehicletracking_backend.simulation.controller;
import com.quangkhai.vehicletracking_backend.simulation.dto.*;
import com.quangkhai.vehicletracking_backend.simulation.service.SimulationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/trips/{tripId}/simulation") @RequiredArgsConstructor
public class SimulationController {
    private final SimulationService service;
    @PostMapping("/play") public SimulationResponse play(@PathVariable long tripId) { return service.play(tripId); }
    @PostMapping("/pause") public SimulationResponse pause(@PathVariable long tripId) { return service.pause(tripId); }
    @PostMapping("/speed") public SimulationResponse speed(@PathVariable long tripId,@Valid @RequestBody SimulationSpeedRequest body) { return service.speed(tripId,body.multiplier()); }
    @PostMapping("/stop") public SimulationResponse stop(@PathVariable long tripId) { return service.stop(tripId); }
    @PostMapping("/reset") public SimulationResponse reset(@PathVariable long tripId) { return service.reset(tripId); }
}
