package com.quangkhai.vehiceltracking_backend.dto;

import com.quangkhai.vehiceltracking_backend.enums.StationType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StationDto {
    private Long id;

    @NotBlank(message = "Mã trạm không được để trống")
    @Size(max = 50, message = "Mã trạm không được vượt quá 50 ký tự")
    private String code;

    @NotBlank(message = "Tên trạm không được để trống")
    @Size(max = 150, message = "Tên trạm không được vượt quá 150 ký tự")
    private String name;

    @NotNull(message = "Vĩ độ (latitude) không được để trống")
    @DecimalMin(value = "-90.0", message = "Vĩ độ phải từ -90 đến 90")
    @DecimalMax(value = "90.0", message = "Vĩ độ phải từ -90 đến 90")
    private Double latitude;

    @NotNull(message = "Kinh độ (longitude) không được để trống")
    @DecimalMin(value = "-180.0", message = "Kinh độ phải từ -180 đến 180")
    @DecimalMax(value = "180.0", message = "Kinh độ phải từ -180 đến 180")
    private Double longitude;

    @Size(max = 255, message = "Địa chỉ không được vượt quá 255 ký tự")
    private String address;

    @NotNull(message = "Bán kính check-in không được để trống")
    @DecimalMin(value = "30.0", message = "Bán kính check-in phải từ 30 đến 150 mét")
    @DecimalMax(value = "150.0", message = "Bán kính check-in phải từ 30 đến 150 mét")
    private Double radiusMeters;

    @NotNull(message = "Loại trạm không được để trống")
    private StationType stationType;

    private LocalDateTime createdAt;
}
