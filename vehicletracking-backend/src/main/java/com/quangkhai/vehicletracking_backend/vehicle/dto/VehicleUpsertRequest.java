package com.quangkhai.vehicletracking_backend.vehicle.dto;

import jakarta.validation.constraints.*;

public record VehicleUpsertRequest(
    @NotBlank(message = "Biển số không được để trống")
    @Size(max = 20, message = "Biển số tối đa 20 ký tự")
    @Pattern(regexp = "[A-Za-z0-9 .\\-]+", message = "Biển số chỉ gồm chữ, số, khoảng trắng, dấu chấm và gạch nối")
    String plateNumber,
    @NotBlank(message = "Tên xe không được để trống")
    @Size(max = 100, message = "Tên xe tối đa 100 ký tự")
    String name,
    @Size(max = 255, message = "Mô tả tối đa 255 ký tự")
    String description
) {}
