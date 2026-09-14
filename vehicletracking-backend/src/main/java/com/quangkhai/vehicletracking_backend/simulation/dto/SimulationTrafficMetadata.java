package com.quangkhai.vehicletracking_backend.simulation.dto;

import com.quangkhai.vehicletracking_backend.traffic.TrafficSource;
import com.quangkhai.vehicletracking_backend.traffic.TrafficStatus;

import java.time.Instant;

/** Additive traffic context for an existing simulator response. */
public record SimulationTrafficMetadata(
        TrafficSource source,
        TrafficStatus status,
        Long nextStopEtaSeconds,
        Instant observedAt,
        Instant fetchedAt,
        boolean blocked,
        String warning
) {}
