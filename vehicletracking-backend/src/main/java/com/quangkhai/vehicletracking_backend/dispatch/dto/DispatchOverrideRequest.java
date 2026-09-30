package com.quangkhai.vehicletracking_backend.dispatch.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DispatchOverrideRequest(@NotNull @Min(0) Long expectedRevision,
                                      @NotNull @Size(min = 10, max = 500) String reason) {}
