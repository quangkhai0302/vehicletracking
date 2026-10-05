package com.quangkhai.vehicletracking_backend.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

import java.nio.charset.StandardCharsets;

public record DriverPasswordChangeRequest(
        @NotBlank(message = "Nhập mật khẩu hiện tại.") String currentPassword,
        @NotBlank(message = "Nhập mật khẩu mới.") String newPassword,
        @NotBlank(message = "Nhập xác nhận mật khẩu mới.") String confirmPassword) {

    @AssertTrue(message = "Mật khẩu mới phải có từ 8 đến 100 ký tự.")
    public boolean isNewPasswordLengthValid() {
        if (newPassword == null) return true;
        int length = newPassword.codePointCount(0, newPassword.length());
        return length >= 8 && length <= 100;
    }

    @AssertTrue(message = "Mật khẩu mới không được vượt quá 72 byte UTF-8.")
    public boolean isNewPasswordWithinBcryptLimit() {
        return newPassword == null || newPassword.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    @AssertTrue(message = "Mật khẩu hiện tại không đúng.")
    public boolean isCurrentPasswordWithinBcryptLimit() {
        return currentPassword == null || currentPassword.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    @Override
    public String toString() {
        return "DriverPasswordChangeRequest[credentials=REDACTED]";
    }
}
