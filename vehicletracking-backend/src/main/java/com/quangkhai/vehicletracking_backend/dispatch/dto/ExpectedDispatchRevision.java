package com.quangkhai.vehicletracking_backend.dispatch.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ExpectedDispatchRevision(@NotNull @Min(0) Long expectedRevision) {}
