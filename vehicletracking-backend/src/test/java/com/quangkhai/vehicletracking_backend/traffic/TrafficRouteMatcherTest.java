package com.quangkhai.vehicletracking_backend.traffic;

import com.quangkhai.vehicletracking_backend.simulation.motion.FlexiblePolyline;
import com.quangkhai.vehicletracking_backend.traffic.matching.TrafficRouteMatcher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrafficRouteMatcherTest {

    private static final String POLYLINE = "BFoz5xJ67i1B1B7PzIhaxL7Y";

    @Test
    void matches_flow_shape_insideRouteCorridor() {
        List<FlexiblePolyline.Point> route = FlexiblePolyline.decode(POLYLINE);
        List<List<Double>> points = route.stream()
                .map(point -> List.of(point.latitude(), point.longitude()))
                .toList();
        TrafficFlowSegment flow = new TrafficFlowSegment("flow", "Route", 100, points, 20, 40, 6, "open", 1.0);

        assertThat(new TrafficRouteMatcher().matches(POLYLINE, flow, 100)).isTrue();
    }

    @Test
    void rejectsMalformedOrDistantFlowShape() {
        TrafficRouteMatcher matcher = new TrafficRouteMatcher();
        TrafficFlowSegment distant = new TrafficFlowSegment("flow", "Distant", 100,
                List.of(List.of(11.0, 107.0), List.of(11.001, 107.001)), 20, 40, 6, "open", 1.0);

        assertThat(matcher.matches("not-a-polyline", distant, 100)).isFalse();
        assertThat(matcher.matches(POLYLINE, distant, 100)).isFalse();
    }
}
