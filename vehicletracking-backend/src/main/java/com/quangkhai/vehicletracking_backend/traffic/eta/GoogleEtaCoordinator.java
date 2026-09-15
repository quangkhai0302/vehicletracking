package com.quangkhai.vehicletracking_backend.traffic.eta;

import com.quangkhai.vehicletracking_backend.route.entity.RouteTransportMode;
import com.quangkhai.vehicletracking_backend.route.entity.RoutingProviderName;
import com.quangkhai.vehicletracking_backend.route.provider.CalculatedRoute;
import com.quangkhai.vehicletracking_backend.route.provider.GoogleRoutingProperties;
import com.quangkhai.vehicletracking_backend.route.provider.RoutingProviderRegistry;
import com.quangkhai.vehicletracking_backend.route.provider.RoutingRequest;
import com.quangkhai.vehicletracking_backend.route.provider.RoutingWaypoint;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Single-flight style TTL cache shared by ETA reads and simulator ticks. */
@Component
public class GoogleEtaCoordinator {
    public record Snapshot(CalculatedRoute route, Instant fetchedAt) {}
    private record Entry(Snapshot snapshot, Instant expiresAt) {}
    private final RoutingProviderRegistry providers;
    private final GoogleRoutingProperties properties;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Snapshot>> inFlight = new ConcurrentHashMap<>();

    public GoogleEtaCoordinator(RoutingProviderRegistry providers, GoogleRoutingProperties properties) {
        this.providers = providers;
        this.properties = properties;
    }

    public Snapshot calculate(String key, java.util.List<RoutingWaypoint> waypoints, RouteTransportMode mode, Instant now) {
        Entry cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(now)) return cached.snapshot();
        CompletableFuture<Snapshot> owner = new CompletableFuture<>();
        CompletableFuture<Snapshot> existing = inFlight.putIfAbsent(key, owner);
        if (existing != null) {
            try { return existing.join(); }
            catch (CompletionException ex) { throw propagate(ex.getCause()); }
        }
        try {
            cached = cache.get(key);
            if (cached != null && cached.expiresAt().isAfter(now)) {
                owner.complete(cached.snapshot());
                return cached.snapshot();
            }
            Snapshot snapshot = new Snapshot(providers.calculate(RoutingProviderName.GOOGLE,
                    new RoutingRequest(waypoints, mode, now, false)), now);
            cache.put(key, new Entry(snapshot, now.plusSeconds(properties.getRefreshSeconds())));
            owner.complete(snapshot);
            return snapshot;
        } catch (RuntimeException ex) {
            owner.completeExceptionally(ex);
            throw ex;
        } finally {
            inFlight.remove(key, owner);
        }
    }

    private RuntimeException propagate(Throwable throwable) {
        return throwable instanceof RuntimeException runtime ? runtime : new IllegalStateException(throwable);
    }
}
