package com.quangkhai.vehicletracking_backend.auth.dto;

import com.quangkhai.vehicletracking_backend.auth.entity.UserAccountEntity;
import com.quangkhai.vehicletracking_backend.auth.entity.UserRole;

public record DriverAccountCreatedResponse(
        Long id,
        String username,
        UserRole role,
        boolean active,
        Long driverId,
        String driverName,
        String driverLicenseNumber,
        String temporaryPassword) {
    public static DriverAccountCreatedResponse from(UserAccountEntity account, String temporaryPassword) {
        UserAccountResponse response = UserAccountResponse.from(account);
        return new DriverAccountCreatedResponse(response.id(), response.username(), response.role(),
                response.active(), response.driverId(), response.driverName(),
                response.driverLicenseNumber(), temporaryPassword);
    }
}
