package com.quangkhai.vehicletracking_backend.traffic.cache;

import com.quangkhai.vehicletracking_backend.traffic.TrafficProvider;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TrafficCache {
    private static final int MAX_ENTRIES = 512;
    private final Map<String, Entry<?>> entries = new ConcurrentHashMap<>();

    public <T> Entry<T> get(String key) {
        @SuppressWarnings("unchecked") Entry<T> entry = (Entry<T>) entries.get(key);
        return entry;
    }

    public <T> void put(String key, TrafficProvider.TrafficPayload<T> payload, Instant fetchedAt) {
        entries.put(key, new Entry<>(payload, fetchedAt));
        if (entries.size() > MAX_ENTRIES) {
            entries.keySet().stream().findFirst().ifPresent(entries::remove);
        }
    }

    public void clear() {
        entries.clear();
    }

    public record Entry<T>(TrafficProvider.TrafficPayload<T> payload, Instant fetchedAt) {}
}
