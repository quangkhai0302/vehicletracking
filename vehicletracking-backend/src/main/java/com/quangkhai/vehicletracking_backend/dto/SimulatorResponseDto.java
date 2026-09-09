package com.quangkhai.vehiceltracking_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulatorResponseDto {
    private String message;
    private String status; // "IDLE", "RUNNING", "PAUSED", "COMPLETED"
    private Long tripId;
    private String simulationRunId; // UUID hoặc null khi IDLE
    private Double multiplier; // Mặc định 1.0
    private Integer currentWaypointIndex;
    private Integer lastPublishedSequence;
}
