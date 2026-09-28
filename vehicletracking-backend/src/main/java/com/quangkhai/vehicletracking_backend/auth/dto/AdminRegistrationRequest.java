package com.quangkhai.vehicletracking_backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Public account creation request. The role is deliberately absent. */
public record AdminRegistrationRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(min = 8, max = 100) String password) {}
