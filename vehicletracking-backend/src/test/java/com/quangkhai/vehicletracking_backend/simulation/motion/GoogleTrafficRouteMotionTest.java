package com.quangkhai.vehicletracking_backend.simulation.motion;

import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
import com.quangkhai.vehicletracking_backend.route.entity.PolylineEncoding;
import com.quangkhai.vehicletracking_backend.route.entity.RouteTrafficInterval;
import com.quangkhai.vehicletracking_backend.route.entity.RouteTransportMode;
import com.quangkhai.vehicletracking_backend.route.entity.RoutingProviderName;
import com.quangkhai.vehicletracking_backend.route.entity.TrafficSpeedCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class GoogleTrafficRouteMotionTest {
    @Test
    void distributesGoogleDurationByTrafficCategoryWithoutChangingTotalDuration() {
        var points = List.of(
                new FlexiblePolyline.Point(10, 106),
                new FlexiblePolyline.Point(10, 106.01),
                new FlexiblePolyline.Point(10, 106.02));
        var section = new RouteDetailResponse.RouteSectionResponse(
                1, 2, encode(points), PolylineEncoding.GOOGLE_ENCODED_POLYLINE,
                2_190, 500, 200,
                List.of(
                        new RouteTrafficInterval(0, 1, TrafficSpeedCategory.NORMAL),
                        new RouteTrafficInterval(1, 2, TrafficSpeedCategory.TRAFFIC_JAM)));
        var stops = List.of(
                stop(1, "START", 10, 106, 0, 0),
                stop(2, "END", 10, 106.02, 2_190, 500));
        Instant now = Instant.parse("2026-09-15T00:00:00Z");
        var route = new RouteDetailResponse(1L, "Google traffic", RouteTransportMode.CAR,
                RoutingProviderName.GOOGLE, 2_190, 500, 200, 0, 500,
                now, now, now, 1, now.plusSeconds(3600), stops, List.of(section), List.of());

        var motion = new RouteMotion(route);
        var normal = motion.at(50);
        var jam = motion.at(300);

        assertThat(motion.duration()).isCloseTo(500, offset(0.0001));
        assertThat(normal.speedKmh()).isGreaterThan(jam.speedKmh() * 3.9);
        assertThat(normal.nextStopSequence()).isEqualTo(2);
        assertThat(jam.nextStopSequence()).isEqualTo(2);
        assertThat(motion.at(500).finished()).isTrue();
    }

    private static RouteDetailResponse.RouteStopResponse stop(
            int sequence, String role, double latitude, double longitude, long distance, long duration) {
        return new RouteDetailResponse.RouteStopResponse(sequence, role, (long) sequence, "Stop " + sequence,
                BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude), 0,
                distance, duration, duration, duration);
    }

    private static String encode(List<FlexiblePolyline.Point> points) {
        StringBuilder result = new StringBuilder();
        long previousLatitude = 0;
        long previousLongitude = 0;
        for (var point : points) {
            long latitude = Math.round(point.latitude() * 100_000);
            long longitude = Math.round(point.longitude() * 100_000);
            append(result, latitude - previousLatitude);
            append(result, longitude - previousLongitude);
            previousLatitude = latitude;
            previousLongitude = longitude;
        }
        return result.toString();
    }

    private static void append(StringBuilder output, long delta) {
        long value = delta < 0 ? ~(delta << 1) : delta << 1;
        while (value >= 0x20) {
            output.append((char) ((0x20 | (value & 0x1f)) + 63));
            value >>= 5;
        }
        output.append((char) (value + 63));
    }
}
