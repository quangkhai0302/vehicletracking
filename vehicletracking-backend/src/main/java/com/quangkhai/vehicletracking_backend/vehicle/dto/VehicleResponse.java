package com.quangkhai.vehicletracking_backend.vehicle.dto;

import com.quangkhai.vehicletracking_backend.driver.dto.DriverSummaryResponse;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleType;
import java.time.Instant;

public record VehicleResponse(Long id, String plateNumber, String name, String description,
                              boolean active, Instant createdAt, Instant updatedAt, VehicleType vehicleType,
                              DriverSummaryResponse driver) {
    public VehicleResponse(Long id, String plateNumber, String name, String description,
                           boolean active, Instant createdAt, Instant updatedAt) {
        this(id, plateNumber, name, description, active, createdAt, updatedAt, VehicleType.CAR, null);
    }
    public VehicleResponse(Long id, String plateNumber, String name, String description,
                           boolean active, Instant createdAt, Instant updatedAt, VehicleType vehicleType) {
        this(id, plateNumber, name, description, active, createdAt, updatedAt, vehicleType, null);
    }
    public static VehicleResponse from(VehicleEntity vehicle) {
        return new VehicleResponse(vehicle.getId(), vehicle.getPlateNumber(), vehicle.getName(),
                vehicle.getDescription(), vehicle.isActive(), vehicle.getCreatedAt(), vehicle.getUpdatedAt(),
                vehicle.getVehicleType(), vehicle.getDriver() == null ? null : DriverSummaryResponse.from(vehicle.getDriver()));
    }
}
