package com.quangkhai.vehicletracking_backend.dispatch.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DeclineDispatchOfferRequest(@NotNull @Min(0) Long expectedRevision,
                                          @Size(min = 3, max = 500) String reason) {}
