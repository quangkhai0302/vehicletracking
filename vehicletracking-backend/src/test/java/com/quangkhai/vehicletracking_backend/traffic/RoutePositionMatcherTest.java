package com.quangkhai.vehicletracking_backend.traffic;

import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
import com.quangkhai.vehicletracking_backend.simulation.motion.FlexiblePolyline;
import com.quangkhai.vehicletracking_backend.traffic.matching.RoutePositionMatcher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoutePositionMatcherTest {
    private static final String POLYLINE = "BFoz5xJ67i1B1B7PzIhaxL7Y";

    @Test
    void projectsStartAndEndOfRemainingSection() {
        var points = FlexiblePolyline.decode(POLYLINE);
        var section = new RouteDetailResponse.RouteSectionResponse(1, 2, POLYLINE, 100, 10, 10);
        var matcher = new RoutePositionMatcher();

        var start = matcher.project(List.of(section), points.getFirst().latitude(), points.getFirst().longitude(), 0, 25);
        var end = matcher.project(List.of(section), points.getLast().latitude(), points.getLast().longitude(), 0, 25);

        assertThat(start).isPresent();
        assertThat(start.orElseThrow().fraction()).isCloseTo(0d, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(end).isPresent();
        assertThat(end.orElseThrow().fraction()).isCloseTo(1d, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void ignoresPositionOutsideConfiguredCorridor() {
        var section = new RouteDetailResponse.RouteSectionResponse(1, 2, POLYLINE, 100, 10, 10);

        assertThat(new RoutePositionMatcher().project(List.of(section), 11, 107, 0, 100)).isEmpty();
    }
}
