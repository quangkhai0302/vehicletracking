package com.quangkhai.vehicletracking_backend.telemetry.dto;

import java.util.List;

public record TelemetryPageResponse(List<TelemetryResponse> items, int page, int size,
                                    long totalElements, int totalPages) {}
