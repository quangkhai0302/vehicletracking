package com.quangkhai.vehicletracking_backend.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DriverAccountCreateRequest(
        @NotNull @Positive Long driverId) {}
