package com.quangkhai.vehicletracking_backend.station.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record StationUpsertRequest(
        @NotBlank(message = "Tên trạm không được để trống")
        @Size(max = 150, message = "Tên trạm không được vượt quá 150 ký tự")
        String name,

        @Size(max = 255, message = "Địa chỉ trạm không được vượt quá 255 ký tự")
        String address,

        @NotNull(message = "Vĩ độ không được để trống")
        @DecimalMin(value = "-90", message = "Vĩ độ tối thiểu là -90")
        @DecimalMax(value = "90", message = "Vĩ độ tối đa là 90")
        BigDecimal latitude,

        @NotNull(message = "Kinh độ không được để trống")
        @DecimalMin(value = "-180", message = "Kinh độ tối thiểu là -180")
        @DecimalMax(value = "180", message = "Kinh độ tối đa là 180")
        BigDecimal longitude,

        @NotNull(message = "Bán kính điểm danh không được để trống")
        @Min(value = 10, message = "Bán kính điểm danh tối thiểu là 10 mét")
        @Max(value = 1000, message = "Bán kính điểm danh tối đa là 1000 mét")
        Integer checkinRadiusMeters
) {
}
