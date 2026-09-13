package com.quangkhai.vehicletracking_backend.telemetry.dto;
import com.quangkhai.vehicletracking_backend.simulation.dto.SimulationResponse;
import com.quangkhai.vehicletracking_backend.trip.dto.TripSummaryResponse;
import java.time.Instant;
import java.util.List;
public record OperationsSnapshot(Instant serverTime,List<TelemetryResponse> positions,
        List<SimulationResponse> simulations,List<TripSummaryResponse> trips) {}
