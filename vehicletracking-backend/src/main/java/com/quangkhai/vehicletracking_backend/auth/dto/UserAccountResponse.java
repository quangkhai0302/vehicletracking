package com.quangkhai.vehicletracking_backend.auth.dto;

import com.quangkhai.vehicletracking_backend.auth.entity.UserAccountEntity;
import com.quangkhai.vehicletracking_backend.auth.entity.UserRole;

public record UserAccountResponse(Long id, String username, UserRole role, boolean active,
                                  Long driverId, String driverName, String driverLicenseNumber) {
    public static UserAccountResponse from(UserAccountEntity account) {
        boolean effectiveActive = account.isActive()
                && (account.getDriver() == null || account.getDriver().isActive());
        return new UserAccountResponse(account.getId(), account.getUsername(), account.getRole(), effectiveActive,
                account.getDriver() == null ? null : account.getDriver().getId(),
                account.getDriver() == null ? null : account.getDriver().getFullName(),
                account.getDriver() == null ? null : account.getDriver().getLicenseNumber());
    }
}
