package com.quangkhai.vehiceltracking_backend.dto;

import com.quangkhai.vehiceltracking_backend.enums.VehicleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleDto {
    private Long id;
    private String plateNumber;
    private String model;
    private VehicleStatus status;
    private Double currentLatitude;
    private Double currentLongitude;
    private Double currentSpeed;
    private Double currentHeading;
    private LocalDateTime lastUpdatedAt;
}
