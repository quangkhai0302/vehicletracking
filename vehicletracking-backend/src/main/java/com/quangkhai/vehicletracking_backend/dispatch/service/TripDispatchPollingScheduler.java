package com.quangkhai.vehicletracking_backend.dispatch.service;

import com.quangkhai.vehicletracking_backend.dispatch.repository.TripDispatchRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Clock;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "trip-dispatch", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TripDispatchPollingScheduler {
    private static final Logger log = LoggerFactory.getLogger(TripDispatchPollingScheduler.class);
    private final TripDispatchRepository dispatches;
    private final TripDispatchJobService jobs;
    private final Clock operationsClock;

    @Scheduled(fixedDelayString = "${trip-dispatch.poll-ms:5000}")
    public void poll() {
        for (Long tripId : dispatches.findDueIds(operationsClock.instant(), PageRequest.of(0, 100))) {
            try {
                jobs.process(tripId);
            } catch (RuntimeException error) {
                log.warn("Dispatch failed tripId={}", tripId, error);
                try { jobs.markStartFailed(tripId); }
                catch (RuntimeException attentionError) {
                    log.error("Could not record dispatch failure tripId={}", tripId, attentionError);
                }
            }
        }
    }
}
