package com.quangkhai.vehicletracking_backend.traffic.eta;

import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
import com.quangkhai.vehicletracking_backend.route.entity.PolylineEncoding;
import com.quangkhai.vehicletracking_backend.route.provider.CalculatedSection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteGeometryCompatibilityTest {
    @Test
    void acceptsSameCorridorWithDifferentPointDensity() {
        var active = List.of(section(encode(10, 106, 10, 106.01, 10, 106.02)));
        var candidate = List.of(calculated(encode(10, 106.0002, 10, 106.005, 10, 106.015, 10, 106.02)));

        assertThat(RouteGeometryCompatibility.equivalent(candidate, active, 0, 10, 106.0002)).isTrue();
    }

    @Test
    void rejectsParallelRoadOutsideThirtyMeterCorridor() {
        var active = List.of(section(encode(10, 106, 10, 106.01, 10, 106.02)));
        // About 55 metres north of the stored path.
        var candidate = List.of(calculated(encode(10.0005, 106, 10.0005, 106.01, 10.0005, 106.02)));

        assertThat(RouteGeometryCompatibility.equivalent(candidate, active, 0, 10.0005, 106)).isFalse();
    }

    private static RouteDetailResponse.RouteSectionResponse section(String encoded) {
        return new RouteDetailResponse.RouteSectionResponse(1, 2, encoded,
                PolylineEncoding.GOOGLE_ENCODED_POLYLINE, 2_000, 300, 200, List.of());
    }

    private static CalculatedSection calculated(String encoded) {
        return new CalculatedSection(1, 2, encoded, PolylineEncoding.GOOGLE_ENCODED_POLYLINE,
                2_000, 300, 200, List.of());
    }

    private static String encode(double... values) {
        StringBuilder result = new StringBuilder();
        long previousLatitude = 0;
        long previousLongitude = 0;
        for (int index = 0; index < values.length; index += 2) {
            long latitude = Math.round(values[index] * 100_000);
            long longitude = Math.round(values[index + 1] * 100_000);
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
