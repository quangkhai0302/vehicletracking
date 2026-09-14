package com.quangkhai.vehicletracking_backend.traffic.eta;

import com.quangkhai.vehicletracking_backend.traffic.TrafficSource;
import com.quangkhai.vehicletracking_backend.traffic.TrafficStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrafficEtaServicePolicyTest {
    @Test
    void trafficRateSlowsOrSpeedsBaselineWithoutAllowingExtremeValues() {
        var live = response(TrafficSource.HERE_LIVE, TrafficStatus.AVAILABLE, 200);
        assertThat(TrafficEtaService.rateFor(live, 100)).isEqualTo(0.5d);

        var faster = response(TrafficSource.HERE_LIVE, TrafficStatus.AVAILABLE, 1);
        assertThat(TrafficEtaService.rateFor(faster, 100)).isEqualTo(1.5d);
    }

    @Test
    void blockedPausesAndRouteSnapshotKeepsBaselineProgress() {
        assertThat(TrafficEtaService.rateFor(response(TrafficSource.HERE_LIVE, TrafficStatus.BLOCKED, 0), 100))
                .isZero();
        assertThat(TrafficEtaService.rateFor(response(TrafficSource.ROUTE_SNAPSHOT, TrafficStatus.AVAILABLE, 200), 100))
                .isEqualTo(1d);
    }

    private TripEtaResponse response(TrafficSource source, TrafficStatus status, long remainingSeconds) {
        return new TripEtaResponse(1, 1, Instant.EPOCH, source, status, null, null, 2,
                100, remainingSeconds, List.of(), List.of(), null);
    }
}
