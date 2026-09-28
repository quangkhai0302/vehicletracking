package com.quangkhai.vehicletracking_backend.vehicle.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record VehicleAssignmentRequest(@NotNull @Positive Long vehicleId) {}
