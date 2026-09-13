package com.quangkhai.vehicletracking_backend.telemetry.service;
import com.quangkhai.vehicletracking_backend.telemetry.dto.OperationsSnapshot;
import jakarta.annotation.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service @RequiredArgsConstructor
public class OperationsStreamService {
    private final OperationsSnapshotService snapshots;
    private final Set<Client> clients=ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService ticker=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("operations-stream").factory());
    private final ExecutorService writers=Executors.newVirtualThreadPerTaskExecutor();
    private static final class Client {
        final SseEmitter emitter=new SseEmitter(600_000L);
        final AtomicBoolean writing=new AtomicBoolean();
    }
    @PostConstruct public void start() { ticker.scheduleWithFixedDelay(this::broadcast,1,1,TimeUnit.SECONDS); }
    public SseEmitter subscribe() {
        var client=new Client();
        client.emitter.onCompletion(()->clients.remove(client));
        client.emitter.onError(error->clients.remove(client));
        client.emitter.onTimeout(()->{ clients.remove(client); client.emitter.complete(); });
        clients.add(client);
        client.writing.set(true);
        writers.submit(()->{
            try { send(client,snapshots.snapshot()); }
            catch(RuntimeException ex) { close(client); }
            finally { client.writing.set(false); }
        });
        return client.emitter;
    }
    private void broadcast() {
        if(clients.isEmpty()) return;
        try {
            var snapshot=snapshots.snapshot();
            for(var client:clients) {
                // One pending write per client. A slow receiver cannot block the simulator or accumulate an event queue.
                if(client.writing.compareAndSet(false,true))
                    writers.submit(()->{ try { send(client,snapshot); } finally { client.writing.set(false); } });
            }
        } catch(RuntimeException ex) { for(var client:clients) close(client); }
    }
    private void send(Client client,OperationsSnapshot snapshot) {
        if(!clients.contains(client)) return;
        try { client.emitter.send(SseEmitter.event().name("snapshot").id(snapshot.serverTime().toString()).reconnectTime(1000).data(snapshot)); }
        catch(Exception ex) { close(client); }
    }
    private void close(Client client) { clients.remove(client); client.emitter.complete(); }
    public int subscriberCount() { return clients.size(); }
    @PreDestroy public void stop() { ticker.shutdownNow(); for(var client:clients) close(client); writers.shutdownNow(); }
}
