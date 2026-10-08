package com.quangkhai.vehicletracking_backend.driverportal.dto;

import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
import com.quangkhai.vehicletracking_backend.simulation.dto.SimulationResponse;
import com.quangkhai.vehicletracking_backend.simulation.motion.RouteMotion.Guidance;
import com.quangkhai.vehicletracking_backend.telemetry.dto.TelemetryResponse;
import com.quangkhai.vehicletracking_backend.trip.dto.TripDetailResponse;
import com.quangkhai.vehicletracking_backend.trip.dto.TripSummaryResponse;
import com.quangkhai.vehicletracking_backend.checkin.dto.TripCheckInsResponse;
import com.quangkhai.vehicletracking_backend.station.dto.StationResponse;
import java.time.Instant;
import java.util.List;

public record DriverNavigationResponse(Instant serverTime, TripSummaryResponse trip,
        List<TripDetailResponse.Stop> stops, RouteDetailResponse route, TelemetryResponse position,
        SimulationResponse simulation, Long routeRevisionId, Guidance guidance,
        TripCheckInsResponse checkIns, List<StationResponse> stations,
        List<com.quangkhai.vehicletracking_backend.simulation.dto.SimulationIncidentResponse> activeIncidents) {}
