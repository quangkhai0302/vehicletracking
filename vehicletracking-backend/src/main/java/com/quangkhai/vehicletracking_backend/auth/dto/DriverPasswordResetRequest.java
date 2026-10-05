package com.quangkhai.vehicletracking_backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;

import java.nio.charset.StandardCharsets;

public record DriverPasswordResetRequest(
        @NotBlank(message = "Nhập mật khẩu mới.") String password) {
    @AssertTrue(message = "Mật khẩu phải có từ 8 đến 100 ký tự.")
    public boolean isPasswordLengthValid() {
        if (password == null) return true;
        int length = password.codePointCount(0, password.length());
        return length >= 8 && length <= 100;
    }

    @AssertTrue(message = "Mật khẩu không được vượt quá 72 byte UTF-8.")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    @Override
    public String toString() {
        return "DriverPasswordResetRequest[password=REDACTED]";
    }
}
