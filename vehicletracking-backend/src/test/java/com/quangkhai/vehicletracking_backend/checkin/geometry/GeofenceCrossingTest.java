package com.quangkhai.vehicletracking_backend.checkin.geometry;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class GeofenceCrossingTest {
    @Test
    void recognizesInsideBoundaryAndZeroLengthSamples() {
        var center = new GeofenceCrossing.Point(10, 20);
        assertThat(GeofenceCrossing.inside(center, 10, 20, 0)).isTrue();
        assertThat(GeofenceCrossing.distance(10, 20, BigDecimal.TEN, BigDecimal.valueOf(20))).isZero();
        assertThat(GeofenceCrossing.firstEntry(center, center, 10, 20, 50))
                .extracting(GeofenceCrossing.Crossing::fraction)
                .isEqualTo(0d);
        assertThat(GeofenceCrossing.firstEntry(new GeofenceCrossing.Point(10.01, 20),
                new GeofenceCrossing.Point(10.01, 20), 10, 20, 50)).isNull();
    }

    @Test
    void interpolatesTheFirstOutsideToInsideCrossing() {
        var crossing = GeofenceCrossing.firstEntry(
                new GeofenceCrossing.Point(10, 19.998),
                new GeofenceCrossing.Point(10, 20.002),
                10, 20, 100);

        assertThat(crossing).isNotNull();
        // At latitude 10°, 0.002° of longitude is about 219 m. The 100 m
        // radius is therefore reached after roughly (219 - 100) / 438 = .27
        // of the segment; this expected value is independent of the helper.
        assertThat(crossing.fraction()).isCloseTo(.27, org.assertj.core.data.Offset.offset(.02));
        assertThat(GeofenceCrossing.distance(crossing.latitude(), crossing.longitude(),
                BigDecimal.TEN, BigDecimal.valueOf(20))).isLessThanOrEqualTo(100.01);
    }

    @Test
    void handlesDatelineAndRejectsNonFiniteInputs() {
        var crossing = GeofenceCrossing.firstEntry(
                new GeofenceCrossing.Point(10, 179.998),
                new GeofenceCrossing.Point(10, -179.998),
                10, 180, 100);
        assertThat(crossing).isNotNull();
        assertThat(crossing.longitude()).isBetween(-180d, 180d);
        assertThat(GeofenceCrossing.distance(new GeofenceCrossing.Point(Double.NaN, 0),
                new GeofenceCrossing.Point(0, 0))).isNaN();
        assertThat(GeofenceCrossing.firstEntry(new GeofenceCrossing.Point(0, 0),
                new GeofenceCrossing.Point(0, 1), 0, 0, Double.NaN)).isNull();
    }
}
