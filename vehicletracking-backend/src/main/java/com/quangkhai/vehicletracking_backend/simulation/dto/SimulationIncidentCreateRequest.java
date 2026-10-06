package com.quangkhai.vehicletracking_backend.simulation.dto;

import java.util.UUID;

import com.quangkhai.vehicletracking_backend.reroute.entity.NotificationSeverity;
import com.quangkhai.vehicletracking_backend.simulation.entity.SimulationIncidentType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SimulationIncidentCreateRequest(
        @Positive int attemptNumber,
        @NotNull SimulationIncidentType type,
        @NotNull NotificationSeverity severity,
        @Size(max = 500) String detail,
        @NotNull UUID idempotencyKey) {}
