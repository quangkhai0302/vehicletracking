package com.quangkhai.vehicletracking_backend.telemetry.dto;
import com.quangkhai.vehicletracking_backend.telemetry.entity.*;
import java.time.Instant;
import java.util.UUID;
public record TelemetryResponse(long id, UUID eventId, long vehicleId, long tripId, Instant recordedAt,
        Instant receivedAt, Instant simulatedAt, double latitude, double longitude, double speedKmh,
        double heading, double accuracyMeters, TelemetrySource source) {
    public static TelemetryResponse from(TelemetrySampleEntity sample) {
        return new TelemetryResponse(sample.getId(),sample.getEventId(),sample.getVehicleId(),sample.getTripId(),
            sample.getRecordedAt(),sample.getReceivedAt(),sample.getSimulatedAt(),sample.getLatitude(),sample.getLongitude(),
            sample.getSpeedKmh(),sample.getHeading(),sample.getAccuracyMeters(),sample.getSource());
    }
}
