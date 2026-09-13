package com.quangkhai.vehicletracking_backend.simulation.dto;
import com.quangkhai.vehicletracking_backend.simulation.entity.*;
import com.quangkhai.vehicletracking_backend.simulation.motion.RouteMotion.Frame;
import java.time.Instant;
public record SimulationResponse(long id,long tripId,SimulationStatus status,int multiplier,double elapsedSeconds,
        double durationSeconds,Instant simulatedAt,Instant updatedAt,String errorMessage,Long replacementTripId,Frame frame) {}
