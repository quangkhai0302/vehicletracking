package com.quangkhai.vehicletracking_backend.telemetry.dto;
import com.quangkhai.vehicletracking_backend.simulation.dto.SimulationResponse;
import com.quangkhai.vehicletracking_backend.trip.dto.TripSummaryResponse;
import java.time.Instant;
import java.util.List;
import com.quangkhai.vehicletracking_backend.checkin.dto.TripCheckInsResponse;
import com.quangkhai.vehicletracking_backend.reroute.dto.NotificationResponse;
public record OperationsSnapshot(Instant serverTime,List<TelemetryResponse> positions,
        List<SimulationResponse> simulations,List<TripSummaryResponse> trips,
        List<TripCheckInsResponse> checkIns, List<NotificationResponse> notifications) {
    public OperationsSnapshot(Instant serverTime, List<TelemetryResponse> positions,
                              List<SimulationResponse> simulations, List<TripSummaryResponse> trips,
                              List<TripCheckInsResponse> checkIns) {
        this(serverTime, positions, simulations, trips, checkIns, List.of());
    }
}
