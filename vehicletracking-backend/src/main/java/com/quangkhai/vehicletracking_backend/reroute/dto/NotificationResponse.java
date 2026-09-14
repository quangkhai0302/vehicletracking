package com.quangkhai.vehicletracking_backend.reroute.dto;

import com.quangkhai.vehicletracking_backend.reroute.entity.*;
import java.time.Instant;

public record NotificationResponse(long id, long tripId, long vehicleId, String vehiclePlateNumber, Long revisionId, NotificationType type,
                                   NotificationSeverity severity, String title, String reason,
                                   String incidentId, String affectedStopSequences,
                                   Long baselineEtaSeconds, Long revisedEtaSeconds,
                                   Instant createdAt, Instant readAt) {
    public static NotificationResponse from(TripNotificationEntity item) {
        return new NotificationResponse(item.getId(), item.getTrip().getId(), item.getTrip().getVehicle().getId(), item.getTrip().getVehiclePlateSnapshot(),
                item.getRevision() == null ? null : item.getRevision().getId(), item.getType(), item.getSeverity(),
                item.getTitle(), item.getReason(), item.getIncidentId(), item.getAffectedStopSequences(),
                item.getBaselineEtaSeconds(), item.getRevisedEtaSeconds(), item.getCreatedAt(), item.getReadAt());
    }
}
