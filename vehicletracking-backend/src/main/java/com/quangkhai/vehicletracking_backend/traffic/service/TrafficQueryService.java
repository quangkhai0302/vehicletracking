package com.quangkhai.vehicletracking_backend.traffic.service;

import com.quangkhai.vehicletracking_backend.config.HereTrafficProperties;
import com.quangkhai.vehicletracking_backend.traffic.*;
import com.quangkhai.vehicletracking_backend.traffic.cache.TrafficCache;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class TrafficQueryService {
    private final TrafficProvider provider;
    private final HereTrafficProperties properties;
    private final Clock operationsClock;
    private final TrafficCache cache = new TrafficCache();
    private final Map<String, LockReference> keyLocks = new ConcurrentHashMap<>();

    public TrafficEnvelope<TrafficFlowSegment> flow(TrafficBounds bounds) {
        ensureEnabled();
        return query("flow:" + bounds.cacheKey(), () -> provider.fetchFlow(bounds));
    }

    public TrafficEnvelope<TrafficIncident> incidents(TrafficBounds bounds) {
        ensureEnabled();
        return query("incidents:" + bounds.cacheKey(), () -> provider.fetchIncidents(bounds));
    }

    public TrafficEnvelope<TrafficFlowSegment> flow(double west, double south, double east, double north) {
        return flow(TrafficBounds.of(west, south, east, north, properties.getMaxBboxSpanHundredths()));
    }

    public TrafficEnvelope<TrafficIncident> incidents(double west, double south, double east, double north) {
        return incidents(TrafficBounds.of(west, south, east, north, properties.getMaxBboxSpanHundredths()));
    }

    public TrafficEnvelope<TrafficFlowSegment> flowForEta(TrafficBounds bounds) {
        if (!properties.isEnabled() || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return unavailable();
        }
        try {
            return flow(bounds);
        } catch (TrafficOperationException ex) {
            return unavailable();
        }
    }

    public TrafficEnvelope<TrafficIncident> incidentsForEta(TrafficBounds bounds) {
        if (!properties.isEnabled() || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return unavailable();
        }
        try {
            return incidents(bounds);
        } catch (TrafficOperationException ex) {
            return unavailable();
        }
    }

    private final Map<String, TileCacheEntry> tileCache = new ConcurrentHashMap<>();

    private record TileCacheEntry(byte[] data, Instant expiresAt) {}

    public byte[] tile(int z, int x, int y) {
        long tileCount = z >= 0 && z <= 20 ? 1L << z : 0;
        if (tileCount == 0 || x < 0 || y < 0 || x >= tileCount || y >= tileCount) {
            return HereTrafficProvider.EMPTY_TILE;
        }
        String key = z + "/" + x + "/" + y;
        Instant now = now();
        TileCacheEntry entry = tileCache.get(key);
        if (entry != null && entry.expiresAt().isAfter(now)) {
            return entry.data();
        }
        byte[] tile = provider.fetchTile(z, x, y);
        if (tile != null && tile.length > 0) {
            if (tileCache.size() > 5000) {
                tileCache.clear();
            }
            tileCache.put(key, new TileCacheEntry(tile, now.plus(Duration.ofSeconds(properties.getCacheTtlSeconds()))));
        }
        return tile != null ? tile : HereTrafficProvider.EMPTY_TILE;
    }

    private <T> TrafficEnvelope<T> query(String key, Supplier<TrafficProvider.TrafficPayload<T>> loader) {
        LockReference lock = acquireLock(key);
        lock.lock.lock();
        try {
            Instant now = now();
            TrafficCache.Entry<T> existing = cache.get(key);
            if (existing != null && ageSeconds(existing.fetchedAt(), now) <= properties.getCacheTtlSeconds()) {
                return envelope(existing.payload(), existing.fetchedAt(), now, TrafficSource.HERE_LIVE,
                        TrafficStatus.AVAILABLE, null);
            }
            try {
                TrafficProvider.TrafficPayload<T> loaded = loader.get();
                Instant fetchedAt = now();
                cache.put(key, loaded, fetchedAt);
                return envelope(loaded, fetchedAt, fetchedAt, TrafficSource.HERE_LIVE,
                        TrafficStatus.AVAILABLE, null);
            } catch (TrafficProviderException ex) {
                if (existing != null && ageSeconds(existing.fetchedAt(), now) <= properties.getStaleTtlSeconds()) {
                    return envelope(existing.payload(), existing.fetchedAt(), now, TrafficSource.HERE_LAST_KNOWN,
                            TrafficStatus.STALE, "Traffic provider unavailable; showing last known data");
                }
                throw mapProviderError(ex);
            }
        } finally {
            lock.lock.unlock();
            releaseLock(key, lock);
        }
    }

    private LockReference acquireLock(String key) {
        return keyLocks.compute(key, (ignored, current) -> {
            LockReference reference = current == null ? new LockReference() : current;
            reference.users++;
            return reference;
        });
    }

    private void releaseLock(String key, LockReference reference) {
        keyLocks.computeIfPresent(key, (ignored, current) -> {
            if (current != reference) return current;
            return --current.users == 0 ? null : current;
        });
    }

    private static final class LockReference {
        private final ReentrantLock lock = new ReentrantLock();
        private int users;
    }

    private <T> TrafficEnvelope<T> envelope(TrafficProvider.TrafficPayload<T> payload, Instant fetchedAt, Instant now,
                                             TrafficSource source, TrafficStatus status, String warning) {
        Instant observed = payload == null ? null : payload.observedAt();
        var results = payload == null ? java.util.List.<T>of() : payload.results();
        return new TrafficEnvelope<>(source, status, observed, fetchedAt,
                ageSeconds(observed == null ? fetchedAt : observed, now), warning, results);
    }

    private TrafficOperationException mapProviderError(TrafficProviderException ex) {
        return switch (ex.getKind()) {
            case TIMEOUT -> new TrafficOperationException(HttpStatus.GATEWAY_TIMEOUT,
                    TrafficErrorCode.TRAFFIC_PROVIDER_TIMEOUT, "Traffic provider request timed out", ex);
            case UNAUTHORIZED -> new TrafficOperationException(HttpStatus.SERVICE_UNAVAILABLE,
                    TrafficErrorCode.TRAFFIC_PROVIDER_UNAUTHORIZED, "Traffic provider credentials are invalid or unauthorized", ex);
            case INVALID_RESPONSE -> new TrafficOperationException(HttpStatus.BAD_GATEWAY,
                    TrafficErrorCode.TRAFFIC_PROVIDER_INVALID_RESPONSE, "Traffic provider returned an invalid response", ex);
            case UNAVAILABLE -> new TrafficOperationException(HttpStatus.SERVICE_UNAVAILABLE,
                    TrafficErrorCode.TRAFFIC_PROVIDER_UNAVAILABLE, "Traffic provider is currently unavailable or rate-limited", ex);
        };
    }

    private void ensureEnabled() {
        if (!properties.isEnabled() || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new TrafficOperationException(HttpStatus.SERVICE_UNAVAILABLE,
                    TrafficErrorCode.TRAFFIC_UNAVAILABLE, "Traffic service is currently disabled or unconfigured");
        }
    }

    private <T> TrafficEnvelope<T> unavailable() {
        Instant now = now();
        return new TrafficEnvelope<>(TrafficSource.UNAVAILABLE, TrafficStatus.UNAVAILABLE,
                null, now, 0, "Traffic provider is unavailable; using route snapshot", java.util.List.of());
    }

    private Instant now() {
        return operationsClock.instant();
    }

    private long ageSeconds(Instant older, Instant now) {
        if (older == null) return 0;
        return Math.max(0, Duration.between(older, now).toSeconds());
    }
}
