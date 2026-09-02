package com.quangkhai.vehiceltracking_backend.dto;

import com.quangkhai.vehiceltracking_backend.enums.VehicleStatus;
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
public class VehicleTelemetryDto {
    private Long vehicleId;
    private String plateNumber;
    private Long tripId;
    private String tripCode;
    private Long routeId;
    private String routeName;
    private Double latitude;
    private Double longitude;
    private Double speed; // km/h
    private Double heading; // degrees (0-360)
    private VehicleStatus status;
    private Integer currentStopIndex;
    private Long targetStationId;
    private String targetStationName;
    private Double distanceToTargetMeters;
    private Long etaSecondsToTarget;
    private List<StationEtaDto> stationsEta;
    private Boolean inIncidentZone;
    private String currentIncidentNotice;
    private LocalDateTime timestamp;
}
