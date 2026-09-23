package com.quangkhai.vehicletracking_backend.driver.dto;

import com.quangkhai.vehicletracking_backend.driver.entity.DriverEntity;

public record DriverSummaryResponse(Long id, String fullName, String phoneNumber, String licenseNumber) {
    public static DriverSummaryResponse from(DriverEntity driver) {
        return new DriverSummaryResponse(driver.getId(), driver.getFullName(), driver.getPhoneNumber(), driver.getLicenseNumber());
    }
}
