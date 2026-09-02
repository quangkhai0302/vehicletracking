package com.quangkhai.vehiceltracking_backend.dto;

import com.quangkhai.vehiceltracking_backend.enums.StationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    private String code;

    @NotBlank(message = "Tên trạm không được để trống")
    private String name;

    @NotNull(message = "Vĩ độ (latitude) không được để trống")
    private Double latitude;

    @NotNull(message = "Kinh độ (longitude) không được để trống")
    private Double longitude;

    private String address;
    private Double radiusMeters;
    private StationType stationType;
    private LocalDateTime createdAt;
}
