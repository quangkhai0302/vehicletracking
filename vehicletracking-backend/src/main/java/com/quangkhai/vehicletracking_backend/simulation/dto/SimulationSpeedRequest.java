package com.quangkhai.vehicletracking_backend.simulation.dto;
import jakarta.validation.constraints.NotNull;
public record SimulationSpeedRequest(@NotNull Integer multiplier) {}
