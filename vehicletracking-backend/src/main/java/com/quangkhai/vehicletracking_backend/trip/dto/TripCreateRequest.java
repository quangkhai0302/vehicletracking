package com.quangkhai.vehicletracking_backend.trip.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record TripCreateRequest(
    @NotNull @Positive Long vehicleId,
    @NotNull @Positive Long routeId,
    @NotNull(message = "Giờ xuất phát không được để trống") Instant scheduledDepartureAt
) {}
