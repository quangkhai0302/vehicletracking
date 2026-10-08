package com.quangkhai.vehicletracking_backend.reroute.dto;

import com.quangkhai.vehicletracking_backend.reroute.entity.*;
import com.quangkhai.vehicletracking_backend.reroute.RerouteMessages;
import com.quangkhai.vehicletracking_backend.simulation.entity.SimulationIncidentEntity;
import java.time.Instant;

public record NotificationResponse(long id, long tripId, long vehicleId, String vehiclePlateNumber, Long revisionId, NotificationType type,
                                   NotificationSeverity severity, String title, String reason,
                                   String incidentId, String affectedStopSequences,
                                   Long baselineEtaSeconds, Long revisedEtaSeconds,
                                   Double measuredDistanceMeters, Double thresholdDistanceMeters, Long breachDurationSeconds,
                                   Instant createdAt, Instant readAt, Long simulationIncidentId,
                                   String simulationIncidentStatus, String simulationIncidentType,
                                   String simulationIncidentReportedByDriver,
                                   String simulationIncidentDetail,
                                   Double simulationIncidentLatitude, Double simulationIncidentLongitude,
                                   Double simulationIncidentElapsedSeconds, String simulationIncidentResolutionNote, Instant simulationIncidentResolvedAt, String simulationIncidentLocationLabel) {
    public static NotificationResponse from(TripNotificationEntity item) {
        SimulationIncidentEntity incident = item.getSimulationIncident();
        return new NotificationResponse(item.getId(), item.getTrip().getId(), item.getTrip().getVehicle().getId(), item.getTrip().getVehiclePlateSnapshot(),
                item.getRevision() == null ? null : item.getRevision().getId(), item.getType(), item.getSeverity(),
                item.getTitle(), RerouteMessages.forDisplay(item.getReason()), item.getIncidentId(), item.getAffectedStopSequences(),
                item.getBaselineEtaSeconds(), item.getRevisedEtaSeconds(), item.getMeasuredDistanceMeters(),
                item.getThresholdDistanceMeters(), item.getBreachDurationSeconds(), item.getCreatedAt(), item.getReadAt(),
                incident == null ? null : incident.getId(),
                incident == null ? null : incident.getStatus().name(),
                incident == null ? null : incident.getType().name(),
                incident == null || incident.getReportedByDriver() == null ? null : incident.getReportedByDriver().getFullName(),
                incident == null ? null : incident.getDetail(),
                incident == null ? null : incident.getLatitude(),
                incident == null ? null : incident.getLongitude(),
                incident == null ? null : incident.getSimulatedElapsedSeconds(),
                incident == null ? null : incident.getResolutionNote(),
                incident == null ? null : incident.getResolvedAt(),
                incident == null ? null : incident.getLocationLabel());
    }
}
