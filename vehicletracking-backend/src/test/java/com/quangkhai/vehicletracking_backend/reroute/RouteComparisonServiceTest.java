package com.quangkhai.vehicletracking_backend.reroute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.quangkhai.vehicletracking_backend.reroute.entity.NotificationSeverity;
import com.quangkhai.vehicletracking_backend.reroute.entity.RerouteReasonCode;
import com.quangkhai.vehicletracking_backend.reroute.entity.TripRouteRevisionEntity;
import com.quangkhai.vehicletracking_backend.reroute.entity.TripRouteRevisionSectionEntity;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripRouteRevisionRepository;
import com.quangkhai.vehicletracking_backend.reroute.service.RouteComparisonGeometry;
import com.quangkhai.vehicletracking_backend.reroute.service.RouteComparisonService;
import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse.RouteSectionResponse;
import com.quangkhai.vehicletracking_backend.simulation.SimulationFixtures;
import com.quangkhai.vehicletracking_backend.simulation.motion.RouteMotion;
import com.quangkhai.vehicletracking_backend.trip.TripFixtures;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;

class RouteComparisonServiceTest {
    final TripRouteRevisionRepository revisions = mock(TripRouteRevisionRepository.class);
    final RouteComparisonService service = new RouteComparisonService(revisions);
    final Instant now = Instant.parse("2026-10-06T04:00:00Z");
    final TripEntity trip = trip();

    TripEntity trip() {
        var a = TripFixtures.station("A"); var b = TripFixtures.station("B");
        ReflectionTestUtils.setField(a,"id",1L); ReflectionTestUtils.setField(b,"id",2L);
        var trip = new TripEntity(new VehicleEntity("COMPARE1","Compare",null),SimulationFixtures.route(a,b),now);
        ReflectionTestUtils.setField(trip,"id",7L);
        return trip;
    }
    TripRouteRevisionEntity revision(long id,int number,List<RouteSectionResponse> sections) {
        var r = new TripRouteRevisionEntity(trip,trip.getRoute(),number,RerouteReasonCode.TRAFFIC_DELAY,"Đổi tuyến thử",null,
                NotificationSeverity.MAJOR,100,30,now);
        ReflectionTestUtils.setField(r,"id",id);
        for (int i=0;i<sections.size();i++) {
            var s=sections.get(i);
            r.addSection(new TripRouteRevisionSectionEntity(i+1,s.destinationStopSequence(),s.encodedPolyline(),s.distanceMeters(),s.travelDurationSeconds(),s.baseTravelDurationSeconds()));
        }
        when(revisions.findById(id)).thenReturn(Optional.of(r));
        return r;
    }
    List<RouteSectionResponse> detour(RouteMotion motion,double elapsed) {
        var frame=motion.at(elapsed);
        return List.of(RouteComparisonGeometryTest.section(2,new double[][]{{frame.latitude(),frame.longitude()},{10.7705,106.702},{10.771,106.701}}),
                RouteComparisonGeometryTest.section(3,new double[][]{{10.771,106.701},{10.77,106.70}}));
    }

    @Test void savedPairRemainsExactAfterSupersessionReplayAndFurtherChanges() {
        var motion = new RouteMotion(RouteDetailResponse.from(trip.getRoute()));
        var sections = detour(motion,5);
        var target=revision(41,1,sections);
        var snapshot = RouteComparisonGeometry.simulation(1,null,motion,5,sections,39,64);
        target.captureComparison(snapshot);
        var first = service.find(7,41);
        target.supersede(now.plusSeconds(5));
        trip.replay(now.plusSeconds(10));
        revision(42,2,detour(motion,10));
        trip.getRoute().getSections().clear();

        assertThat(service.find(7,41)).isEqualTo(first);
        assertThat(first.status()).isEqualTo("AVAILABLE");
        assertThat(first.attemptNumber()).isEqualTo(1);
        assertThat(first.before()).isEqualTo(snapshot.before());
        verify(revisions,never()).findAllByTripIdOrderByRevisionNumberDesc(anyLong());
        verify(revisions,never()).save(any());
        assertThatThrownBy(() -> target.captureComparison(snapshot)).isInstanceOf(IllegalStateException.class);
    }

    @Test void revisionMustBelongToRequestedTrip() {
        revision(41,1,detour(new RouteMotion(RouteDetailResponse.from(trip.getRoute())),5));
        assertThatThrownBy(() -> service.find(8,41)).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(404));
        assertThatThrownBy(() -> service.find(7,999)).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(404));
        verify(revisions,never()).findAllByTripIdOrderByRevisionNumberDesc(anyLong());
    }

    @Test void legacyReconstructsOnlySameAttemptAppliedPredecessorsEvenAfterReplay() {
        var baseline = new RouteMotion(RouteDetailResponse.from(trip.getRoute()));
        var firstSections=detour(baseline,5);
        var predecessor=revision(41,1,firstSections); predecessor.applyToSimulation(5,1); predecessor.supersede(now.plusSeconds(2));
        baseline.revise(firstSections,5);
        var targetSections=detour(baseline,10);
        var target=revision(42,2,targetSections); target.applyToSimulation(10,1);
        var later=revision(43,3,List.of(RouteComparisonGeometryTest.section(2,new double[][]{{10.77,106.70},{10.771,106.701}})));
        later.applyToSimulation(1,2);
        when(revisions.findAllByTripIdOrderByRevisionNumberDesc(7L)).thenReturn(List.of(later,target,predecessor));
        trip.replay(now.plusSeconds(20));

        var result=service.find(7,42);
        assertThat(result.status()).isEqualTo("RECONSTRUCTED");
        assertThat(result.attemptNumber()).isEqualTo(1);
        assertThat(result.before().encodedPolylines()).isEqualTo(baseline.remainingSections(10).stream().map(RouteSectionResponse::encodedPolyline).toList());
        assertThat(result.after().encodedPolylines()).isEqualTo(targetSections.stream().map(RouteSectionResponse::encodedPolyline).toList());
        assertThat(target.getComparisonSnapshot()).isNull();
        verify(revisions,never()).save(any());
    }

    @Test void legacyLiveMissingAnchorsIsAfterOnlyAndNeverReadsCurrentRoute() {
        var target=revision(41,1,detour(new RouteMotion(RouteDetailResponse.from(trip.getRoute())),5));
        var result=service.find(7,41);
        assertThat(result.status()).isEqualTo("UNAVAILABLE");
        assertThat(result.before()).isNull();
        assertThat(result.after().encodedPolylines()).isEqualTo(target.getSections().stream().map(TripRouteRevisionSectionEntity::getEncodedPolyline).toList());
        assertThat(result.message()).contains("Chỉ hiển thị đường sau");
        verify(revisions,never()).findAllByTripIdOrderByRevisionNumberDesc(anyLong());
    }

    @Test void legacyActivationAtDifferentPositionIsNotMisrepresentedAsCreation() {
        var target=revision(41,1,detour(new RouteMotion(RouteDetailResponse.from(trip.getRoute())),5));
        target.applyToSimulation(10,1);
        when(revisions.findAllByTripIdOrderByRevisionNumberDesc(7L)).thenReturn(List.of(target));
        assertThat(service.find(7,41).status()).isEqualTo("UNAVAILABLE");
        assertThat(service.find(7,41).before()).isNull();
        assertThat(service.find(7,41).after()).isNotNull();
    }

    @Test void incompleteLegacyGeometryStillReturnsReadableUnavailableResponse() {
        revision(41,1,List.of());
        var result=service.find(7,41);
        assertThat(result.status()).isEqualTo("UNAVAILABLE");
        assertThat(result.before()).isNull();
        assertThat(result.after()).isNull();
        assertThat(result.anchor()).isNull();
    }
}
