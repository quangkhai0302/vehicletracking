package com.quangkhai.vehicletracking_backend.simulation.dto;
import java.time.Instant;

import com.quangkhai.vehicletracking_backend.simulation.entity.SimulationStatus;
import com.quangkhai.vehicletracking_backend.simulation.motion.RouteMotion.Frame;
public record SimulationResponse(long id,long tripId,SimulationStatus status,int multiplier,double elapsedSeconds,
        double durationSeconds,Instant simulatedAt,Instant updatedAt,String errorMessage,Long replacementTripId,
        Frame frame, SimulationTrafficMetadata traffic, int attemptNumber, Long routeRevisionId,
        Double virtualElapsedSeconds) {}
