package com.quangkhai.vehicletracking_backend.simulation;
import com.quangkhai.vehicletracking_backend.simulation.motion.*;
import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
import com.quangkhai.vehicletracking_backend.trip.TripFixtures;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
class RouteMotionTest {
    private RouteMotion motion() {
        var a=TripFixtures.station("A"); var b=TripFixtures.station("B");
        ReflectionTestUtils.setField(a,"id",1L); ReflectionTestUtils.setField(b,"id",2L);
        return new RouteMotion(RouteDetailResponse.from(SimulationFixtures.route(a,b)));
    }
    @Test void decodesHereOfficialExample() {
        var points=FlexiblePolyline.decode("BFoz5xJ67i1B1B7PzIhaxL7Y");
        assertThat(points).hasSize(4);
        assertThat(points.getFirst().latitude()).isEqualTo(50.10228);
        assertThat(points.getLast().longitude()).isEqualTo(8.68752);
    }
    @Test void decodesThirdDimensionWithoutUsingItAsLongitude() {
        // Header 0x15 = precision 5, third dimension present, followed by zero height.
        assertThat(FlexiblePolyline.decode("BVAAACAACAA")).hasSize(3);
    }
    @ParameterizedTest @ValueSource(strings={"","A","BF","BF!","BF_","BF________________A","BFggggggggggggg","BF999999999999999999999"})
    void rejectsMalformedGeometry(String encoded) { assertThatThrownBy(()->FlexiblePolyline.decode(encoded)).isInstanceOf(RuntimeException.class); }
    @Test void interpolatesByDistanceInsteadOfPointIndex() {
        var sample=motion().at(10);
        assertThat(sample.latitude()).isCloseTo(10.7705,within(0.000002));
        assertThat(sample.longitude()).isCloseTo(106.7005,within(0.000002));
        assertThat(sample.progressPercent()).isCloseTo(25,within(.01));
        assertThat(sample.nextStopSequence()).isEqualTo(2);
        assertThat(sample.nextStopEtaSeconds()).isEqualTo(10);
    }
    @Test void dwellAndLoopRespectStopOccurrence() {
        var motion=motion(); var dwelling=motion.at(22);
        assertThat(dwelling.dwelling()).isTrue(); assertThat(dwelling.speedKmh()).isZero();
        assertThat(dwelling.latitude()).isEqualTo(10.771); assertThat(dwelling.nextStopSequence()).isEqualTo(3);
        assertThat(dwelling.nextStopEtaSeconds()).isEqualTo(22);
        assertThat(motion.at(24).dwelling()).isFalse();
        assertThat(motion.at(44).finished()).isTrue();
        assertThat(motion.at(44).latitude()).isEqualTo(10.77);
        assertThat(motion.at(44).progressPercent()).isEqualTo(100);
    }
    @Test void boundaryAndOutsideTimeAreClamped() {
        assertThat(motion().at(-4).progressPercent()).isZero();
        assertThat(motion().at(500).speedKmh()).isZero();
        assertThatThrownBy(()->motion().at(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void incompleteGeometryCannotBecomeStraightLineFallback() {
        var a=TripFixtures.station("A");var b=TripFixtures.station("B");
        ReflectionTestUtils.setField(a,"id",1L);ReflectionTestUtils.setField(b,"id",2L);
        assertThatThrownBy(()->new RouteMotion(RouteDetailResponse.from(TripFixtures.route(a,b)))).isInstanceOf(IllegalArgumentException.class);
    }
}
