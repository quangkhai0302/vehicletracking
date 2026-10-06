package com.quangkhai.vehicletracking_backend.checkin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record EmployeeBoardingCountRequest(@NotNull @Min(0) Integer employeeBoardingCount) {}
