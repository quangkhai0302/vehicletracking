package com.quangkhai.vehicletracking_backend.schedule.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TripSchedulePollingScheduler {
    private final TripScheduleGenerationService generation;

    @Scheduled(fixedDelayString = "${trip-scheduling.poll-interval-ms:60000}")
    public void poll() { generation.generateUpcomingTrips(); }
}
