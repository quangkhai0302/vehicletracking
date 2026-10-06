package com.quangkhai.vehicletracking_backend.simulation.dto;

import java.time.Instant;

import com.quangkhai.vehicletracking_backend.reroute.entity.NotificationSeverity;
import com.quangkhai.vehicletracking_backend.simulation.entity.SimulationIncidentEntity;
import com.quangkhai.vehicletracking_backend.simulation.entity.SimulationIncidentStatus;
import com.quangkhai.vehicletracking_backend.simulation.entity.SimulationIncidentType;

public record SimulationIncidentResponse(long id, long tripId, long vehicleId, String vehiclePlateNumber,
                                         Long reportedByDriverId, String reportedByDriverName,
                                         int attemptNumber, SimulationIncidentType type,
                                         NotificationSeverity severity, SimulationIncidentStatus status,
                                         String detail, double latitude, double longitude,
                                         double simulatedElapsedSeconds, Instant createdAt,
                                         Instant acknowledgedAt, Instant resolvedAt,
                                         SimulationResponse simulation) {
    public static SimulationIncidentResponse from(SimulationIncidentEntity incident,
                                                   SimulationResponse simulation) {
        return new SimulationIncidentResponse(incident.getId(), incident.getTrip().getId(),
                incident.getTrip().getVehicle().getId(), incident.getTrip().getVehiclePlateSnapshot(),
                incident.getReportedByDriver() == null ? null : incident.getReportedByDriver().getId(),
                incident.getReportedByDriver() == null ? null : incident.getReportedByDriver().getFullName(),
                incident.getAttemptNumber(), incident.getType(), incident.getSeverity(), incident.getStatus(),
                incident.getDetail(), incident.getLatitude(), incident.getLongitude(),
                incident.getSimulatedElapsedSeconds(), incident.getCreatedAt(), incident.getAcknowledgedAt(),
                incident.getResolvedAt(), simulation);
    }
}
