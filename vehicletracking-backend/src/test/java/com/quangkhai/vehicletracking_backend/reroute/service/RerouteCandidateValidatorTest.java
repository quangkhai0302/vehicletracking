package com.quangkhai.vehicletracking_backend.reroute.service;

import com.quangkhai.vehicletracking_backend.route.provider.CalculatedRoute;
import com.quangkhai.vehicletracking_backend.route.provider.CalculatedSection;
import com.quangkhai.vehicletracking_backend.simulation.SimulationFixtures;
import com.quangkhai.vehicletracking_backend.traffic.TrafficSource;
import com.quangkhai.vehicletracking_backend.traffic.TrafficStatus;
import com.quangkhai.vehicletracking_backend.traffic.eta.TripEtaResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RerouteCandidateValidatorTest {
    @Test
    void rejectsCandidateWhichStillCrossesTriggeringClosure() {
        var candidate = route(new double[][]{{10.7700, 106.7000}, {10.7710, 106.7010}});
        var eta = eta(List.of(List.of(10.7705, 106.7005)));

        assertThat(RerouteCandidateValidator.intersectsTriggeringClosure(candidate, eta, 30)).isTrue();
    }

    @Test
    void acceptsCandidateOutsideTriggeringClosureCorridor() {
        var candidate = route(new double[][]{{10.7700, 106.7000}, {10.7710, 106.7010}});
        var eta = eta(List.of(List.of(10.7800, 106.7100)));

        assertThat(RerouteCandidateValidator.intersectsTriggeringClosure(candidate, eta, 30)).isFalse();
    }

    private CalculatedRoute route(double[][] points) {
        return new CalculatedRoute(Instant.parse("2026-09-15T08:00:00Z"), List.of(
                new CalculatedSection(1, 2, SimulationFixtures.encode(points), 160, 20, 18)));
    }

    private TripEtaResponse eta(List<List<Double>> incidentPoints) {
        Instant now = Instant.parse("2026-09-15T08:00:00Z");
        return new TripEtaResponse(1, 2, now, TrafficSource.HERE_LIVE, TrafficStatus.BLOCKED,
                now, now, 2, 20, 0, List.of(), List.of(new TripEtaResponse.AffectedSegment(
                1, 2, "INCIDENT", "closure-1", 0, "road_closure", incidentPoints, List.of())),
                "TRAFFIC_BLOCKED", 1, 1, null);
    }
}
