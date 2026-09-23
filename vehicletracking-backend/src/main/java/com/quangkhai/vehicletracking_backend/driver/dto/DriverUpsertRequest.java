package com.quangkhai.vehicletracking_backend.driver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DriverUpsertRequest(
        @NotBlank(message = "Họ tên tài xế không được để trống")
        @Size(max = 100, message = "Họ tên tài xế tối đa 100 ký tự")
        String fullName,
        @NotBlank(message = "Số điện thoại không được để trống")
        @Size(min = 7, max = 20, message = "Số điện thoại phải từ 7 đến 20 ký tự")
        @Pattern(regexp = "\\+?[0-9][0-9 .()\\-]{6,19}", message = "Số điện thoại không hợp lệ")
        String phoneNumber,
        @NotBlank(message = "Số GPLX không được để trống")
        @Size(max = 50, message = "Số GPLX tối đa 50 ký tự")
        @Pattern(regexp = "[A-Za-z0-9.\\-]+", message = "Số GPLX chỉ gồm chữ, số, dấu chấm và gạch nối")
        String licenseNumber
) {
    public DriverUpsertRequest {
        fullName = fullName == null ? null : fullName.trim();
        phoneNumber = phoneNumber == null ? null : phoneNumber.trim();
        licenseNumber = licenseNumber == null ? null : licenseNumber.trim();
    }
}
