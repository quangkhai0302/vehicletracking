package com.quangkhai.vehiceltracking_backend.dto;

import com.quangkhai.vehiceltracking_backend.enums.CheckInStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripCheckInDto {
    private Long id;
    private Long stationId;
    private String stationName;
    private String stationCode;
    private Double latitude;
    private Double longitude;
    private Integer stopOrder;
    private LocalDateTime scheduledArrivalTime;
    private LocalDateTime actualArrivalTime;
    private CheckInStatus status;
}
