package com.quangkhai.vehicletracking_backend.driver.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DriverAssignmentRequest(@NotNull @Positive Long driverId) {}
