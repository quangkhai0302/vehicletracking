package com.quangkhai.vehicletracking_backend.trip.dto;

import jakarta.validation.constraints.*;

public record TripCreateRequest(
    @NotNull @Positive Long vehicleId,
    @NotNull @Positive Long routeId,
    @Positive Long driverId
) {}
