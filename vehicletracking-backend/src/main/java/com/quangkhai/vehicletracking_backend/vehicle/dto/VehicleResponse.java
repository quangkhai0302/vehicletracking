package com.quangkhai.vehicletracking_backend.vehicle.dto;

import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import java.time.Instant;

public record VehicleResponse(Long id, String plateNumber, String name, String description,
                              boolean active, Instant createdAt, Instant updatedAt) {
    public static VehicleResponse from(VehicleEntity vehicle) {
        return new VehicleResponse(vehicle.getId(), vehicle.getPlateNumber(), vehicle.getName(),
                vehicle.getDescription(), vehicle.isActive(), vehicle.getCreatedAt(), vehicle.getUpdatedAt());
    }
}
