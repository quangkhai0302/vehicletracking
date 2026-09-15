package com.quangkhai.vehicletracking_backend.reroute.service;

import com.quangkhai.vehicletracking_backend.traffic.TrafficStatus;
import com.quangkhai.vehicletracking_backend.traffic.eta.TripEtaResponse;
import com.quangkhai.vehicletracking_backend.config.RerouteProperties;
import java.time.Duration;
import java.time.Instant;

/** Pure, deterministic gate for consecutive live-traffic breaches. */
public final class ReroutePolicy {
    public static final long MIN_DELAY_SECONDS = 600;
    public static final int MIN_DELAY_PERCENT = 30;
    public static final long COOLDOWN_SECONDS = 300;
    private ReroutePolicy() {}

    public static Decision observe(TripEtaResponse eta, Instant previousFetchedAt, String previousFingerprint,
                                   int previousCount, String lastTriggeredFingerprint, Instant lastTriggeredAt,
                                   String fingerprint, Instant now) {
        return observe(eta, previousFetchedAt, previousFingerprint, previousCount, lastTriggeredFingerprint,
                lastTriggeredAt, fingerprint, now, null);
    }

    public static Decision observe(TripEtaResponse eta, Instant previousFetchedAt, String previousFingerprint,
                                   int previousCount, String lastTriggeredFingerprint, Instant lastTriggeredAt,
                                   String fingerprint, Instant now, RerouteProperties properties) {
        long delaySeconds = properties == null ? MIN_DELAY_SECONDS : properties.getDelaySeconds();
        int delayPercent = properties == null ? MIN_DELAY_PERCENT : properties.getDelayPercent();
        long cooldownSeconds = properties == null ? COOLDOWN_SECONDS : properties.getCooldownSeconds();
        int requiredFetches = properties == null ? 2 : properties.getConsecutiveFetches();
        if (eta == null || eta.trafficFetchedAt() == null
                || (eta.source() != com.quangkhai.vehicletracking_backend.traffic.TrafficSource.HERE_LIVE
                    && eta.source() != com.quangkhai.vehicletracking_backend.traffic.TrafficSource.HERE_LAST_KNOWN
                    && eta.source() != com.quangkhai.vehicletracking_backend.traffic.TrafficSource.GOOGLE_LIVE
                    && eta.source() != com.quangkhai.vehicletracking_backend.traffic.TrafficSource.GOOGLE_LAST_KNOWN))
            return new Decision(false, false, 0, fingerprint, "TRAFFIC_SOURCE_UNAVAILABLE");
        if (previousFetchedAt != null && !eta.trafficFetchedAt().isAfter(previousFetchedAt))
            return new Decision(false, false, previousCount, previousFingerprint, "TRAFFIC_NOT_REFRESHED");
        boolean breach = eta.status() == TrafficStatus.BLOCKED
                || (eta.totalRemainingSeconds() - eta.baselineRemainingSeconds() >= delaySeconds
                    && eta.baselineRemainingSeconds() > 0
                    && (eta.totalRemainingSeconds() * 100L >= eta.baselineRemainingSeconds() * (100L + delayPercent)));
        if (!breach) return new Decision(false, false, 0, null, "TRAFFIC_WITHIN_POLICY");
        int count = fingerprint != null && fingerprint.equals(previousFingerprint) ? previousCount + 1 : 1;
        boolean cooldown = lastTriggeredAt != null && Duration.between(lastTriggeredAt, now).getSeconds() < cooldownSeconds;
        boolean trigger = fingerprint != null && count >= requiredFetches && !cooldown
                && !fingerprint.equals(lastTriggeredFingerprint == null ? "" : lastTriggeredFingerprint);
        return new Decision(true, trigger, count, fingerprint, cooldown ? "REROUTE_COOLDOWN" : "TRAFFIC_BREACH");
    }

    public record Decision(boolean breach, boolean trigger, int consecutiveCount, String fingerprint, String reason) {}
}
