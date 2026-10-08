package com.quangkhai.vehicletracking_backend.simulation.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SimulationIncidentResolveRequest(@Positive int attemptNumber, @Size(max = 500) String resolutionNote) {}
