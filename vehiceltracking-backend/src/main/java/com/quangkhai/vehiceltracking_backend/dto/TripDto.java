package com.quangkhai.vehiceltracking_backend.dto;

import com.quangkhai.vehiceltracking_backend.enums.TripStatus;
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
public class TripDto {
    private Long id;
    private String tripCode;
    private Long routeId;
    private String routeName;
    private Long vehicleId;
    private String vehiclePlateNumber;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private TripStatus status;
    private List<TripCheckInDto> checkIns;
    private LocalDateTime createdAt;
}
