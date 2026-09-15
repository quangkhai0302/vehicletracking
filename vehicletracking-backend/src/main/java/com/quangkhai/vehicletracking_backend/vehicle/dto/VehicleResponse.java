package com.quangkhai.vehicletracking_backend.vehicle.dto;

import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleType;
import java.time.Instant;

public record VehicleResponse(Long id, String plateNumber, String name, String description,
                              boolean active, Instant createdAt, Instant updatedAt, VehicleType vehicleType) {
    public VehicleResponse(Long id, String plateNumber, String name, String description,
                           boolean active, Instant createdAt, Instant updatedAt) {
        this(id, plateNumber, name, description, active, createdAt, updatedAt, VehicleType.CAR);
    }
    public static VehicleResponse from(VehicleEntity vehicle) {
        return new VehicleResponse(vehicle.getId(), vehicle.getPlateNumber(), vehicle.getName(),
                vehicle.getDescription(), vehicle.isActive(), vehicle.getCreatedAt(), vehicle.getUpdatedAt(),
                vehicle.getVehicleType());
    }
}
