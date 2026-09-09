package com.quangkhai.vehiceltracking_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteResponseDto {
    private Long id;
    private String code;
    private String name;
    private String description;
    private Double totalDistanceKm;
    private Double estimatedDurationMinutes;
    private List<RouteStationDto> stations;
    private LocalDateTime createdAt;
}
