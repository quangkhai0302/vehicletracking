package com.quangkhai.vehicletracking_backend.reroute;

import com.quangkhai.vehicletracking_backend.reroute.service.ReroutePolicy;
import com.quangkhai.vehicletracking_backend.traffic.TrafficSource;
import com.quangkhai.vehicletracking_backend.traffic.TrafficStatus;
import com.quangkhai.vehicletracking_backend.traffic.eta.TripEtaResponse;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ReroutePolicyTest {
    private final Instant first = Instant.parse("2026-01-01T00:00:00Z");
    private TripEtaResponse delayed(Instant fetched) {
        return new TripEtaResponse(1, 1, fetched, TrafficSource.HERE_LIVE, TrafficStatus.AVAILABLE,
                fetched, fetched, 2, 600, 1300, List.of(), List.of(), "delay");
    }

    @Test
    void requiresTwoDistinctTrafficFetchesForSameBreach() {
        var one = ReroutePolicy.observe(delayed(first), null, null, 0, null, null, "DELAY:x", first);
        assertThat(one.trigger()).isFalse();
        var two = ReroutePolicy.observe(delayed(first.plusSeconds(60)), first, one.fingerprint(), one.consecutiveCount(),
                null, null, "DELAY:x", first.plusSeconds(60));
        assertThat(two.trigger()).isTrue();
    }

    @Test
    void cooldownSuppressesRepeatedFingerprint() {
        var decision = ReroutePolicy.observe(delayed(first.plusSeconds(60)), first, "DELAY:x", 1,
                null, first, "DELAY:x", first.plusSeconds(60));
        assertThat(decision.trigger()).isFalse();
        assertThat(decision.reason()).isEqualTo("REROUTE_COOLDOWN");
    }

    @Test
    void sameTrafficFetchTimestampIsNotCountedTwice() {
        var one = ReroutePolicy.observe(delayed(first), null, null, 0, null, null, "DELAY:x", first);
        var repeat = ReroutePolicy.observe(delayed(first), first, one.fingerprint(), one.consecutiveCount(),
                null, null, "DELAY:x", first.plusSeconds(1));
        assertThat(repeat.trigger()).isFalse();
        assertThat(repeat.reason()).isEqualTo("TRAFFIC_NOT_REFRESHED");
    }

    @Test
    void blockedTrafficUsesTheSameTwoFetchGate() {
        var blocked = new TripEtaResponse(1, 1, first, TrafficSource.HERE_LIVE, TrafficStatus.BLOCKED,
                first, first, 2, 600, 0, List.of(), List.of(), "blocked");
        var one = ReroutePolicy.observe(blocked, null, null, 0, null, null, "BLOCKED:incident", first);
        var blockedAgain = new TripEtaResponse(1, 1, first.plusSeconds(60), TrafficSource.HERE_LIVE, TrafficStatus.BLOCKED,
                first.plusSeconds(60), first.plusSeconds(60), 2, 600, 0, List.of(), List.of(), "blocked");
        var two = ReroutePolicy.observe(blockedAgain, first,
                one.fingerprint(), one.consecutiveCount(), null, null, "BLOCKED:incident", first.plusSeconds(60));
        assertThat(two.trigger()).isTrue();
    }
}
