package com.quangkhai.vehicletracking_backend.reroute;

import com.quangkhai.vehicletracking_backend.reroute.entity.*;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripRouteRevisionRepository;
import com.quangkhai.vehicletracking_backend.reroute.service.TripRouteGeometryService;
import com.quangkhai.vehicletracking_backend.simulation.SimulationFixtures;
import com.quangkhai.vehicletracking_backend.trip.TripFixtures;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TripRouteGeometryServiceTest {
    final TripRouteRevisionRepository revisions=mock(TripRouteRevisionRepository.class);
    final TripRouteGeometryService geometry=new TripRouteGeometryService(revisions);
    final TripEntity trip=trip();
    private TripEntity trip() {
        var a=TripFixtures.station("A");var b=TripFixtures.station("B");
        ReflectionTestUtils.setField(a,"id",1L);ReflectionTestUtils.setField(b,"id",2L);
        var trip=new TripEntity(new VehicleEntity("TEST-1","Test",null),SimulationFixtures.route(a,b),Instant.now());
        ReflectionTestUtils.setField(trip,"id",1L);
        return trip;
    }
    private TripRouteRevisionEntity detour() {
        var anchor=geometry.resolve(trip).motion().at(5);
        var revision=new TripRouteRevisionEntity(trip,trip.getRoute(),1,RerouteReasonCode.TRAFFIC_DELAY,"detour",null,
            NotificationSeverity.MAJOR,100,44,Instant.now());
        ReflectionTestUtils.setField(revision,"id",10L);
        revision.addSection(new TripRouteRevisionSectionEntity(1,2,SimulationFixtures.encode(new double[][]{
            {anchor.latitude(),anchor.longitude()},{10.77025,106.702},{10.771,106.701}}),400,20,20));
        revision.addSection(new TripRouteRevisionSectionEntity(2,3,SimulationFixtures.encode(new double[][]{
            {10.771,106.701},{10.77,106.70}}),156,20,20));
        return revision;
    }
    @Test void appliesOnceWithoutMutatingOriginalCacheAndReconstructsAfterRestart() {
        var original=geometry.resolve(trip);var revision=detour();
        when(revisions.findAllByTripIdOrderByRevisionNumberDesc(1L)).thenReturn(List.of(revision));
        when(revisions.findTopByTripIdAndStatusOrderByRevisionNumberDesc(1L,RouteRevisionStatus.ACTIVE)).thenReturn(Optional.of(revision));
        var applied=geometry.applyActive(trip,5);
        assertThat(applied.revisionId()).isEqualTo(10L);
        assertThat(applied.motion().at(15).longitude()).isGreaterThan(106.701);
        assertThat(original.motion().duration()).isEqualTo(44);
        geometry.applyActive(trip,10);
        assertThat(revision.getSimulationStartElapsed()).isEqualTo(5);
        assertThat(new TripRouteGeometryService(revisions).resolve(trip).motion().at(15)).isEqualTo(applied.motion().at(15));
    }
    @Test void replayExcludesGeometryFromPreviousAttempt() {
        var revision=detour();revision.applyToSimulation(5,1);
        when(revisions.findAllByTripIdOrderByRevisionNumberDesc(1L)).thenReturn(List.of(revision));
        assertThat(geometry.resolve(trip).revisionId()).isEqualTo(10L);
        trip.replay(Instant.now());
        assertThat(geometry.resolve(trip).revisionId()).isNull();
        assertThat(geometry.resolve(trip).motion().duration()).isEqualTo(44);
    }
    @Test void readOriginalGeometryDoesNotRequireSimulatablePolyline() {
        trip.getRoute().getSections().clear();
        assertThat(geometry.route(trip).sections()).isEmpty();
    }
}
