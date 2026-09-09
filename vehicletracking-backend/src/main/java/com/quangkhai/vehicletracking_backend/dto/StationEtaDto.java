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
public class StationEtaDto {
    private Long stationId;
    private String stationName;
    private String stationCode;
    private Integer stopOrder;
    private Double distanceRemainingMeters;
    private Long etaSeconds; // Số giây dự kiến đến trạm này
    private LocalDateTime estimatedArrivalTime; // Thời điểm dự kiến đến trạm
    private CheckInStatus status;
}
