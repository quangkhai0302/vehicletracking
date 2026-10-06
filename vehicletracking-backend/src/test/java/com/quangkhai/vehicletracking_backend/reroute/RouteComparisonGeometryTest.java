package com.quangkhai.vehicletracking_backend.reroute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.quangkhai.vehicletracking_backend.reroute.entity.RouteComparisonSnapshot;
import com.quangkhai.vehicletracking_backend.reroute.service.RouteComparisonGeometry;
import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse.RouteSectionResponse;
import com.quangkhai.vehicletracking_backend.simulation.SimulationFixtures;
import com.quangkhai.vehicletracking_backend.simulation.motion.FlexiblePolyline;
import com.quangkhai.vehicletracking_backend.simulation.motion.RouteMotion;
import com.quangkhai.vehicletracking_backend.trip.TripFixtures;

class RouteComparisonGeometryTest {
    static RouteMotion motion() {
        return new RouteMotion(RouteDetailResponse.from(SimulationFixtures.route(TripFixtures.station("A"), TripFixtures.station("B"))));
    }
    static RouteSectionResponse section(int destination, double[][] points) {
        return new RouteSectionResponse(1, destination, SimulationFixtures.encode(points), 400, 30, 30);
    }

    @Test void partialMotionStartsBothPathsAtEventAndOmitsTravelledPrefix() {
        var motion = motion();
        var anchor = motion.at(10);
        var changed = List.of(section(2, new double[][]{{anchor.latitude(), anchor.longitude()}, {10.7705,106.702},{10.771,106.701}}));
        var snapshot = RouteComparisonGeometry.simulation(1, null, motion, 10, changed, 34, 30);

        var first = FlexiblePolyline.decode(snapshot.before().encodedPolylines().getFirst());
        assertThat(first.getFirst().latitude()).isCloseTo(anchor.latitude(), within(.00001));
        assertThat(first.getFirst().longitude()).isCloseTo(anchor.longitude(), within(.00001));
        assertThat(first).doesNotContain(new FlexiblePolyline.Point(10.77,106.70));
        assertThat(snapshot.before().encodedPolylines()).hasSize(2);
        assertThat(snapshot.before().durationSeconds()).isEqualTo(34);
        assertThat(snapshot.after().durationSeconds()).isEqualTo(30);
    }

    @Test void secondChangeUsesImmediatelyPrecedingDetourAndDoesNotMutateFirstPair() {
        var motion = motion();
        var anchor = motion.at(5);
        var firstDetour = List.of(section(2, new double[][]{{anchor.latitude(),anchor.longitude()},{10.7705,106.702},{10.771,106.701}}),
                section(3,new double[][]{{10.771,106.701},{10.77,106.70}}));
        var first = RouteComparisonGeometry.simulation(1, null, motion, 5, firstDetour, 39, 64);
        motion.revise(firstDetour, 5);
        var nextAnchor = motion.at(10);
        var second = RouteComparisonGeometry.simulation(1, 41L, motion, 10,
                List.of(section(2,new double[][]{{nextAnchor.latitude(),nextAnchor.longitude()},{10.771,106.701}})),59,25);

        assertThat(FlexiblePolyline.decode(second.before().encodedPolylines().getFirst()))
                .contains(new FlexiblePolyline.Point(10.7705,106.702));
        assertThat(second.previousRevisionId()).isEqualTo(41L);
        assertThat(first.before().encodedPolylines()).noneSatisfy(p -> assertThat(FlexiblePolyline.decode(p))
                .contains(new FlexiblePolyline.Point(10.7705,106.702)));
    }

    @Test void simulationRejectsMismatchedActivationOriginAndNonMovingFrames() {
        var motion = motion();
        var wrong = List.of(section(2,new double[][]{{10.77,106.70},{10.771,106.701}}));
        assertThatThrownBy(() -> RouteComparisonGeometry.simulation(1,null,motion,10,wrong,34,30))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> motion.remainingSections(21)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> motion.remainingSections(44)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void gpsClipsOnlyNextDestinationAndPreservesSubsequentSections() {
        var before = List.of(section(2,new double[][]{{10.77,106.70},{10.771,106.701}}),
                section(3,new double[][]{{10.771,106.701},{10.77,106.70}}));
        var after = List.of(section(2,new double[][]{{10.7705,106.7005},{10.7705,106.702},{10.771,106.701}}));
        var snapshot = RouteComparisonGeometry.gps(1,8L,before,after,10.7705,106.7005,75,50);
        var first = FlexiblePolyline.decode(snapshot.before().encodedPolylines().getFirst());
        assertThat(first).containsExactly(new FlexiblePolyline.Point(10.7705,106.7005),new FlexiblePolyline.Point(10.771,106.701));
        assertThat(snapshot.before().encodedPolylines().getLast()).isEqualTo(before.getLast().encodedPolyline());
        assertThat(snapshot.before().distanceMeters()).isGreaterThan(200).isLessThan(260);
    }

    @Test void gpsRejectsAmbiguousLoopAndOffRouteOrigin() {
        var loop = List.of(section(2,new double[][]{{10.77,106.70},{10.77,106.702},{10.771,106.702},{10.771,106.70},{10.77,106.70},{10.77,106.702}}));
        var after = List.of(section(2,new double[][]{{10.77,106.701},{10.771,106.702}}));
        assertThatThrownBy(() -> RouteComparisonGeometry.gps(1,null,loop,after,10.77,106.701,50,20))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Ambiguous");
        assertThatThrownBy(() -> RouteComparisonGeometry.gps(1,null,loop,after,10.78,106.701,50,20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void snapshotOwnsDefensiveGeometryCopies() {
        var polylines = new ArrayList<>(List.of(section(2,new double[][]{{10.77,106.70},{10.771,106.701}}).encodedPolyline()));
        var path = new RouteComparisonSnapshot.Path(polylines,156,20L);
        polylines.clear();
        assertThat(path.encodedPolylines()).hasSize(1);
        assertThatThrownBy(() -> path.encodedPolylines().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void gpsRejectsAfterPathWithDifferentOriginRatherThanDrawingFakeConnector() {
        var before=List.of(section(2,new double[][]{{10.77,106.70},{10.771,106.701}}));
        var after=List.of(section(2,new double[][]{{10.77,106.703},{10.771,106.701}}));
        assertThatThrownBy(() -> RouteComparisonGeometry.gps(1,null,before,after,10.7705,106.7005,50,20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void remainingDistanceDoesNotDependOnHowManyVerticesEncodeTheSameRoad() {
        var dense = new double[31][2];
        for (int i = 0; i < dense.length; i++) dense[i] = new double[]{10.77 + i * .00001, 106.70};
        var sparse = new double[][]{dense[0], dense[30]};
        var distance = RouteComparisonGeometry.path(List.of(section(2, dense)), 30L).distanceMeters();
        assertThat(distance).isEqualTo(33);
        assertThat(RouteComparisonGeometry.path(List.of(section(2, sparse)), 30L).distanceMeters()).isEqualTo(distance);
    }
}
