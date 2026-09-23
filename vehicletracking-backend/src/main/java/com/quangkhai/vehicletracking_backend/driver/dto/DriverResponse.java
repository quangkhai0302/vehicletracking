package com.quangkhai.vehicletracking_backend.driver.dto;

import com.quangkhai.vehicletracking_backend.driver.entity.DriverEntity;

import java.time.Instant;

public record DriverResponse(Long id, String fullName, String phoneNumber, String licenseNumber,
                             boolean active, Instant createdAt, Instant updatedAt) {
    public static DriverResponse from(DriverEntity driver) {
        return new DriverResponse(driver.getId(), driver.getFullName(), driver.getPhoneNumber(),
                driver.getLicenseNumber(), driver.isActive(), driver.getCreatedAt(), driver.getUpdatedAt());
    }
}
