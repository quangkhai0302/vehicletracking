package com.quangkhai.vehicletracking_backend.traffic.eta;

import com.quangkhai.vehicletracking_backend.traffic.TrafficSource;
import com.quangkhai.vehicletracking_backend.traffic.TrafficStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrafficEtaServicePolicyTest {
    @Test
    void trafficRateSlowsOrSpeedsBaselineWithoutAnArtificialMultiplierCap() {
        var live = response(TrafficSource.HERE_LIVE, TrafficStatus.AVAILABLE, 200);
        assertThat(TrafficEtaService.rateFor(live, 100)).isEqualTo(0.5d);

        var faster = response(TrafficSource.HERE_LIVE, TrafficStatus.AVAILABLE, 1);
        assertThat(TrafficEtaService.rateFor(faster, 100)).isEqualTo(100d);
    }

    @Test
    void blockedPausesAndRouteSnapshotKeepsBaselineProgress() {
        assertThat(TrafficEtaService.rateFor(response(TrafficSource.HERE_LIVE, TrafficStatus.BLOCKED, 0), 100))
                .isZero();
        assertThat(TrafficEtaService.rateFor(response(TrafficSource.ROUTE_SNAPSHOT, TrafficStatus.AVAILABLE, 200), 100))
                .isEqualTo(1d);
    }

    @Test
    void sectionRateUsesTheTrafficAtTheVehicleInsteadOfTheWholeRemainingRoute() {
        // A 10-second free-flow section that currently takes 50 seconds must crawl at 20%.
        assertThat(TrafficEtaService.rateForSection(10, 50)).isEqualTo(0.2d);
        assertThat(TrafficEtaService.rateForSection(10, 1)).isEqualTo(10d);
        // Standstill-like flow is represented as a very slow crawl, not free-flow movement.
        assertThat(TrafficEtaService.rateForSection(10, 1_000)).isEqualTo(0.01d);
    }

    @Test void tenKmhSnapshotCanFollowThirtySevenKmhLiveFlow() {
        double length=1000, baselineSeconds=360;
        double rate=TrafficEtaService.rateForSection(baselineSeconds, length/(37d/3.6d));
        assertThat(length/baselineSeconds*3.6d*rate).isCloseTo(37d, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(TrafficEtaService.rateForSection(10, Double.NaN)).isEqualTo(1d);
    }

    private TripEtaResponse response(TrafficSource source, TrafficStatus status, long remainingSeconds) {
        return new TripEtaResponse(1, 1, Instant.EPOCH, source, status, null, null, 2,
                100, remainingSeconds, List.of(), List.of(), null);
    }
}
