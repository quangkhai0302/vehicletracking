package com.quangkhai.vehicletracking_backend.driverportal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DriverRouteApplyRequest(@NotNull @Min(0) @Max(2) Integer optionIndex) {}
