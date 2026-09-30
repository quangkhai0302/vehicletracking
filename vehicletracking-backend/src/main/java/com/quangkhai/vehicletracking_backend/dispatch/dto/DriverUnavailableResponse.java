package com.quangkhai.vehicletracking_backend.dispatch.dto;

import com.quangkhai.vehicletracking_backend.dispatch.entity.DispatchState;

public record DriverUnavailableResponse(long tripId, DispatchState state, long revision) {}
