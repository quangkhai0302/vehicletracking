package com.quangkhai.vehicletracking_backend.simulation.service;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.util.concurrent.*;
@Component @RequiredArgsConstructor
@ConditionalOnProperty(name="app.simulation.scheduling-enabled",havingValue="true",matchIfMissing=true)
public class SimulationScheduler {
    private final SimulationService service;
    private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("simulation-clock").factory());
    private static final System.Logger LOG=System.getLogger(SimulationScheduler.class.getName());
    @EventListener(ApplicationReadyEvent.class) public void start() {
        for(var trip:service.activeTripIds()) {
            try { service.recover(trip); } catch(RuntimeException ex) { markFailed(trip); }
        }
        executor.scheduleWithFixedDelay(this::tick,1,1,TimeUnit.SECONDS);
    }
    private void tick() {
        try {
            for(var trip:service.activeTripIds()) {
                try { service.tick(trip); } catch(RuntimeException ex) { markFailed(trip); }
            }
        } catch(RuntimeException ex) { LOG.log(System.Logger.Level.WARNING,"Simulator database unavailable; retry next tick."); }
    }
    private void markFailed(long trip) {
        LOG.log(System.Logger.Level.WARNING,"Simulation failed for trip "+trip);
        try { service.fail(trip); } catch(RuntimeException ex) { LOG.log(System.Logger.Level.WARNING,"Cannot persist simulation failure for trip "+trip); }
    }
    @PreDestroy public void close() { executor.shutdownNow(); }
}
