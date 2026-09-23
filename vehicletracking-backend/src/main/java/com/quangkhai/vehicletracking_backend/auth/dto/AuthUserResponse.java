package com.quangkhai.vehicletracking_backend.auth.dto;

import com.quangkhai.vehicletracking_backend.auth.config.SecurityConfig.UserAccountPrincipal;
import com.quangkhai.vehicletracking_backend.auth.entity.UserRole;

public record AuthUserResponse(long accountId, String username, UserRole role, boolean active,
                               Long driverId, String driverName) {
    public static AuthUserResponse from(UserAccountPrincipal principal) {
        return new AuthUserResponse(principal.accountId(), principal.usernameValue(), principal.role(),
                principal.isEnabled(), principal.driverId(), principal.driverName());
    }
}
