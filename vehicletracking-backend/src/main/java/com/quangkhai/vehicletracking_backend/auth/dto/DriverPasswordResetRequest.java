package com.quangkhai.vehicletracking_backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DriverPasswordResetRequest(
        @NotBlank @Size(min = 8, max = 100) String password) {
}
