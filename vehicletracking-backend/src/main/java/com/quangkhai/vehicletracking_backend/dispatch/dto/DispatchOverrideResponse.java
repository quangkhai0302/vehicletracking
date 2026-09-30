package com.quangkhai.vehicletracking_backend.dispatch.dto;

import com.quangkhai.vehicletracking_backend.simulation.dto.SimulationResponse;

public record DispatchOverrideResponse(DispatchDetail dispatch, SimulationResponse simulation) {}
