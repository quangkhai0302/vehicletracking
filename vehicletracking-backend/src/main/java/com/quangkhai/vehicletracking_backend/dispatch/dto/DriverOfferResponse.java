package com.quangkhai.vehicletracking_backend.dispatch.dto;

import java.time.Instant;
import java.util.UUID;

public record DriverOfferResponse(UUID offerId, long tripId, String routeName, String vehiclePlate,
                                  Instant scheduledDepartureAt, Instant cutoffAt, Instant expiresAt,
                                  long revision) {}
