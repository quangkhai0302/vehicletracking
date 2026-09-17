package com.quangkhai.vehicletracking_backend.traffic.eta;

import com.quangkhai.vehicletracking_backend.traffic.TrafficSource;
import com.quangkhai.vehicletracking_backend.traffic.TrafficStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrafficEtaServicePolicyTest {
    @Test
    void matchCacheIgnoresEnvelopeAgeButRefreshesWhenFlowChanges() {
        var service = new TrafficEtaService(null, null, null, null,
                new com.quangkhai.vehicletracking_backend.config.HereTrafficProperties(),
                java.time.Clock.systemUTC(), null);
        String line = "BFoz5xJ67i1B1B7PzIhaxL7Y";
        var points = com.quangkhai.vehicletracking_backend.simulation.motion.FlexiblePolyline.decode(line)
                .stream().map(p -> List.of(p.latitude(), p.longitude())).toList();
        var flow = new com.quangkhai.vehicletracking_backend.traffic.TrafficFlowSegment("id", "road", 100,
                points, 20, 40, 1, "open", 1d);
        var section = new com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse.RouteSectionResponse(1, 2, line, 100, 10, 10);
        var first = new com.quangkhai.vehicletracking_backend.traffic.TrafficEnvelope<>(TrafficSource.HERE_LIVE,
                TrafficStatus.AVAILABLE, Instant.EPOCH, Instant.EPOCH, 0, null, List.of(flow));
        var aged = new com.quangkhai.vehicletracking_backend.traffic.TrafficEnvelope<>(TrafficSource.HERE_LAST_KNOWN,
                TrafficStatus.AVAILABLE, Instant.EPOCH, Instant.EPOCH, 9, null, List.of(flow));
        Object a = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "matchingFlows", section, first);
        Object b = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "matchingFlows", section, aged);
        assertThat(a).isSameAs(b);
        var changed = new com.quangkhai.vehicletracking_backend.traffic.TrafficFlowSegment("id", "road", 100,
                points, 5, 40, 8, "open", 1d);
        var fresh = new com.quangkhai.vehicletracking_backend.traffic.TrafficEnvelope<>(TrafficSource.HERE_LIVE,
                TrafficStatus.AVAILABLE, Instant.EPOCH, Instant.EPOCH, 0, null, List.of(changed));
        Object c = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "matchingFlows", section, fresh);
        assertThat(c).isNotSameAs(a);
    }

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
