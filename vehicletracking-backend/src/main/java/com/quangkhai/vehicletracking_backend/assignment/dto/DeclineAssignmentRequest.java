package com.quangkhai.vehicletracking_backend.assignment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeclineAssignmentRequest(
        @NotBlank(message = "Lý do từ chối không được để trống")
        @Size(min = 3, max = 500, message = "Lý do từ chối phải dài từ 3 đến 500 ký tự")
        String reason) {}
