package com.quangkhai.vehicletracking_backend.simulation.dto;
import com.quangkhai.vehicletracking_backend.simulation.entity.SimulationScenario;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
public record SimulationScenarioRequest(@NotNull SimulationScenario scenario, @Min(1) int attemptNumber) {}
