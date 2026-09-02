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
public class AlertMessageDto {
    private String id;
    private String level; // INFO, WARNING, DANGER
    private String title;
    private String message;
    private Long tripId;
    private Long vehicleId;
    private Long incidentId;
    private LocalDateTime timestamp;
}
