package com.quangkhai.vehiceltracking_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteStationDto {
    private Long id;
    private Integer stopOrder;
    private StationDto station;
    private Double distanceToNextKm;
    private Double estimatedTimeToNextMinutes;
}
