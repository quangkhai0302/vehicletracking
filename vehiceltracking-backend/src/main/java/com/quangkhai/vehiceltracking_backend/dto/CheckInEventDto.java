package com.quangkhai.vehiceltracking_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckInEventDto {
    private Long tripId;
    private String tripCode;
    private Long vehicleId;
    private String plateNumber;
    private Long stationId;
    private String stationName;
    private Integer stopOrder;
    private LocalDateTime checkInTime;
    private String message;
}
