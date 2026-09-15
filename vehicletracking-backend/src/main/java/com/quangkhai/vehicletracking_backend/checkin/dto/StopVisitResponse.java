package com.quangkhai.vehicletracking_backend.checkin.dto;

import com.quangkhai.vehicletracking_backend.checkin.entity.*;
import com.quangkhai.vehicletracking_backend.telemetry.entity.TelemetrySource;
import java.time.Instant;

public record StopVisitResponse(long id, long tripId, int stopSequence, TelemetrySource source,
        CheckInEvidenceKind evidenceKind, Instant actualArrivalAt, Instant simulatedArrivalAt,
        Instant detectedAt, Long fromSampleId, long toSampleId, double evidenceFraction,
        double latitude, double longitude, int attemptNumber) {
    public static StopVisitResponse from(TripStopVisitEntity visit) {
        return new StopVisitResponse(visit.getId(),visit.getTrip().getId(),visit.getStopSequence(),visit.getSource(),
            visit.getEvidenceKind(),visit.getActualArrivalAt(),visit.getSimulatedArrivalAt(),visit.getDetectedAt(),
            visit.getFromSample()==null?null:visit.getFromSample().getId(),visit.getToSample().getId(),visit.getEvidenceFraction(),
            visit.getLatitude(),visit.getLongitude(),visit.getAttemptNumber());
    }
}
