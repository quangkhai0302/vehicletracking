package com.quangkhai.vehicletracking_backend.trip.service;

import com.quangkhai.vehicletracking_backend.trip.event.TripStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Runs the provider call after the lifecycle transaction has committed. */
@Component
public class TripStartedScheduleListener {
    private static final Logger log = LoggerFactory.getLogger(TripStartedScheduleListener.class);
    private final TripStartScheduleService schedule;

    public TripStartedScheduleListener(TripStartScheduleService schedule) {
        this.schedule = schedule;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void refreshSchedule(TripStartedEvent event) {
        try {
            schedule.refresh(event.tripId());
        } catch (RuntimeException ex) {
            // A provider outage must not undo or obscure a successfully
            // committed trip start. The stored route schedule remains usable.
            log.warn("Unable to refresh trip schedule from traffic tripId={} reason={}", event.tripId(), ex.getMessage());
        }
    }
}
