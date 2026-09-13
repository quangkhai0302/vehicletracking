package com.quangkhai.vehicletracking_backend.telemetry.dto;

import com.quangkhai.vehicletracking_backend.telemetry.entity.TelemetrySource;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public record TelemetryRequest(
        @NotNull UUID eventId, @NotNull @Positive Long vehicleId, @NotNull @Positive Long tripId,
        @NotNull Instant recordedAt,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @NotNull @DecimalMin("0") @DecimalMax("500") Double speedKmh,
        @NotNull @DecimalMin("0") @DecimalMax(value="360", inclusive=false) Double heading,
        @NotNull @DecimalMin("0") @DecimalMax("10000") Double accuracyMeters,
        @NotNull TelemetrySource source) {}
