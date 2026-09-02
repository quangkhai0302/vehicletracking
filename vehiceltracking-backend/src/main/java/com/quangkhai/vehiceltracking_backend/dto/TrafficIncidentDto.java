package com.quangkhai.vehiceltracking_backend.dto;

import com.quangkhai.vehiceltracking_backend.enums.IncidentType;
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
public class TrafficIncidentDto {
    private Long id;

    @NotBlank(message = "Tiêu đề sự cố không được để trống")
    private String title;

    private IncidentType type;

    @NotNull(message = "Tọa độ vĩ độ không được để trống")
    private Double latitude;

    @NotNull(message = "Tọa độ kinh độ không được để trống")
    private Double longitude;

    private Double radiusMeters;
    private Double speedReductionPercent;
    private String description;
    private Boolean active;
    private LocalDateTime createdAt;
}
