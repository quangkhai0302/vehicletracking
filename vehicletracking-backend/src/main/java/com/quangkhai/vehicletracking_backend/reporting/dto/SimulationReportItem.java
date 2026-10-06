package com.quangkhai.vehicletracking_backend.reporting.dto;
import java.time.Instant;
import java.util.List;

import com.quangkhai.vehicletracking_backend.simulation.entity.SimulationScenario;
import com.quangkhai.vehicletracking_backend.simulation.entity.SimulationStatus;
public record SimulationReportItem(long tripId, int attemptNumber, boolean current,
        Long vehicleId, String vehiclePlateNumber, Long driverId, String driverName, String routeName,
        Instant startedAt, Instant endedAt, SimulationStatus status, SimulationScenario scenario,
        Double plannedDurationSeconds, Double plannedDistanceMeters, Double virtualElapsedSeconds,
        double progressSeconds, Double latenessSeconds, SimulationPunctuality punctuality,
        boolean metadataComplete, long offRouteEventCount, List<SimulationReportRevision> routeRevisions) {}

