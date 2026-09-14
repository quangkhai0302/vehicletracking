package com.quangkhai.vehicletracking_backend.trip.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** Only the schedule is mutable; vehicle/route are immutable snapshots after creation. */
public record TripUpdateRequest(@NotNull(message = "Giờ xuất phát không được để trống") Instant scheduledDepartureAt) {}
