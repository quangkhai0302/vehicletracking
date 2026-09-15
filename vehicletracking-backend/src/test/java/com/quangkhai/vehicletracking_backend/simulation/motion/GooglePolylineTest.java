package com.quangkhai.vehicletracking_backend.simulation.motion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

class GooglePolylineTest {
    @Test
    void decodesOfficialEncodedPolylineExample() {
        var points = GooglePolyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@");
        assertThat(points).hasSize(3);
        assertThat(points.get(0).latitude()).isCloseTo(38.5, offset(0.00001));
        assertThat(points.get(0).longitude()).isCloseTo(-120.2, offset(0.00001));
        assertThat(points.get(2).latitude()).isCloseTo(43.252, offset(0.00001));
        assertThat(points.get(2).longitude()).isCloseTo(-126.453, offset(0.00001));
    }

    @Test
    void rejectsTruncatedOrOutOfRangeGeometry() {
        assertThatThrownBy(() -> GooglePolyline.decode("_p~iF~ps|U_"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
