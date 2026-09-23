package com.quangkhai.vehicletracking_backend.trip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelTripRequest(
        @NotBlank(message = "Lý do hủy chuyến không được để trống")
        @Size(min = 3, max = 500, message = "Lý do hủy chuyến phải dài từ 3 đến 500 ký tự")
        String reason
) {}
