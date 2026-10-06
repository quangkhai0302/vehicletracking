package com.quangkhai.vehicletracking_backend.reporting.dto;
import java.time.Instant;
public record SimulationReportRevision(long revisionId, int revisionNumber, Instant createdAt,
        long baselineEtaSeconds, long revisedEtaSeconds) {}

