package com.quangkhai.vehicletracking_backend.simulation;

import com.quangkhai.vehicletracking_backend.checkin.entity.TripCheckInStateEntity;
import com.quangkhai.vehicletracking_backend.checkin.repository.TripCheckInStateRepository;
import com.quangkhai.vehicletracking_backend.reroute.entity.*;
import com.quangkhai.vehicletracking_backend.reroute.repository.*;
import com.quangkhai.vehicletracking_backend.simulation.entity.*;
import com.quangkhai.vehicletracking_backend.simulation.repository.*;
import com.quangkhai.vehicletracking_backend.simulation.service.SimulationService;
import com.quangkhai.vehicletracking_backend.telemetry.entity.TelemetrySource;
import com.quangkhai.vehicletracking_backend.telemetry.repository.*;
import com.quangkhai.vehicletracking_backend.telemetry.service.TelemetryService;
import com.quangkhai.vehicletracking_backend.traffic.eta.TrafficEtaService;
import com.quangkhai.vehicletracking_backend.trip.TripFixtures;
import com.quangkhai.vehicletracking_backend.trip.entity.*;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.trip.service.TripService;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import com.quangkhai.vehicletracking_backend.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimulationReplayTest {
    final Instant now=Instant.parse("2026-09-15T02:00:00Z");
    SimulationRepository runs=mock(SimulationRepository.class);
    TripRepository trips=mock(TripRepository.class);
    VehicleRepository vehicles=mock(VehicleRepository.class);
    TripService tripService=mock(TripService.class);
    TelemetryService telemetry=mock(TelemetryService.class);
    TelemetryRepository samples=mock(TelemetryRepository.class);
    VehiclePositionRepository positions=mock(VehiclePositionRepository.class);
    TrafficEtaService eta=mock(TrafficEtaService.class);
    SimulationAttemptRepository attempts=mock(SimulationAttemptRepository.class);
    TripCheckInStateRepository checkpoints=mock(TripCheckInStateRepository.class);
    TripTrafficAlertStateRepository alerts=mock(TripTrafficAlertStateRepository.class);
    TripRouteRevisionRepository revisions=mock(TripRouteRevisionRepository.class);
    SimulationService service;
    TripEntity trip;
    SimulationRunEntity run;

    @BeforeEach void setup() {
        var vehicle=new VehicleEntity("TEST123","Test",null);
        ReflectionTestUtils.setField(vehicle,"id",1L);
        var a=TripFixtures.station("A"); var b=TripFixtures.station("B");
        ReflectionTestUtils.setField(a,"id",1L); ReflectionTestUtils.setField(b,"id",2L);
        var route=SimulationFixtures.route(a,b); ReflectionTestUtils.setField(route,"id",7L);
        trip=new TripEntity(vehicle,route,now.minusSeconds(120)); ReflectionTestUtils.setField(trip,"id",5L);
        trip.start(now.minusSeconds(90));
        run=new SimulationRunEntity(5L,now.minusSeconds(90)); ReflectionTestUtils.setField(run,"id",9L);
        run.advance(15,now.minusSeconds(10)); run.changeStatus(SimulationStatus.RUNNING,now.minusSeconds(10));
        run.changeMultiplier(10,now.minusSeconds(10));
        when(trips.findLockedById(5L)).thenReturn(Optional.of(trip));
        when(trips.findAllByVehicleIdOrderByScheduledDepartureAtDescIdDesc(1L)).thenReturn(List.of(trip));
        when(vehicles.findLockedById(1L)).thenReturn(Optional.of(vehicle));
        when(runs.findByTripId(5L)).thenReturn(Optional.of(run));
        service=new SimulationService(runs,trips,vehicles,tripService,telemetry,samples,positions,Clock.fixed(now,ZoneOffset.UTC),eta,
            attempts,checkpoints,alerts,revisions,new com.quangkhai.vehicletracking_backend.reroute.service.TripRouteGeometryService(revisions));
    }

    @Test void realtimeSnapshotDoesNotCalculateTrafficOrCallProvider() {
        var response = service.describeSnapshot(trip, run);
        assertThat(response.frame()).isNotNull();
        verify(eta).latestForSnapshot(trip);
        verify(eta, never()).calculate(anyLong());
        verify(eta, never()).simulationRate(anyLong(), anyDouble());
    }

    @Test void liveFlowSpeedIsNotClampedInFrameOrTelemetry() {
        var route=trip.getRoute();
        for (var section:route.getSections()) ReflectionTestUtils.setField(section,"baseTravelDurationSeconds",56L);
        var motion=new com.quangkhai.vehicletracking_backend.simulation.motion.RouteMotion(
            com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse.from(route));
        double rate=37d/motion.at(0).speedKmh();
        assertThat(rate).isGreaterThan(1.5d);
        when(eta.simulationRate(eq(5L),anyDouble())).thenReturn(rate);
        run.advance(0,now.minusSeconds(1)); run.changeMultiplier(1,now.minusSeconds(1));
        service.tick(5L);
        var request=ArgumentCaptor.forClass(com.quangkhai.vehicletracking_backend.telemetry.dto.TelemetryRequest.class);
        verify(telemetry).ingestSimulator(request.capture(),any());
        assertThat(request.getValue().speedKmh()).isCloseTo(37d,within(1e-8));
        assertThat(service.describe(trip,run).frame().speedKmh()).isCloseTo(37d,within(1e-8));
        assertThat(run.getElapsedSeconds()).isCloseTo(rate,within(1e-8));
    }

    @Test void failedMalformedRouteStillHasInspectableResponse() {
        run.fail("Invalid route",now);
        ReflectionTestUtils.setField(trip.getRoute().getSections().getFirst(),"encodedPolyline","broken");
        assertThat(service.describe(trip,run).status()).isEqualTo(SimulationStatus.FAILED);
    }

    @ParameterizedTest @EnumSource(SimulationStatus.class)
    void replayKeepsIdentityArchivesOldStateAndResetsEveryStatus(SimulationStatus status) {
        run.changeStatus(status,now.minusSeconds(10));
        if (status==SimulationStatus.COMPLETED) trip.complete(now.minusSeconds(10));
        if (status==SimulationStatus.STOPPED) trip.cancel(now.minusSeconds(10));
        if (status==SimulationStatus.FAILED) run.fail("Old failure",now.minusSeconds(10));
        var state=new TripCheckInStateEntity(trip,3,true,null,100);
        var alert=new TripTrafficAlertStateEntity(trip,now);
        alert.markTriggered("old",now); alert.observe(now,"old",2,now);
        var revision=mock(TripRouteRevisionEntity.class);
        when(checkpoints.findById(5L)).thenReturn(Optional.of(state));
        when(alerts.findById(5L)).thenReturn(Optional.of(alert));
        when(revisions.findTopByTripIdAndStatusOrderByRevisionNumberDesc(5L,RouteRevisionStatus.ACTIVE)).thenReturn(Optional.of(revision));
        var response=service.reset(5L);
        assertThat(response.tripId()).isEqualTo(5L); assertThat(response.id()).isEqualTo(9L);
        assertThat(trip.getRoute().getId()).isEqualTo(7L);
        assertThat(response.attemptNumber()).isEqualTo(2); assertThat(response.status()).isEqualTo(SimulationStatus.PAUSED);
        assertThat(response.elapsedSeconds()).isZero(); assertThat(response.multiplier()).isEqualTo(1);
        assertThat(response.errorMessage()).isNull(); assertThat(response.replacementTripId()).isNull();
        assertThat(trip.getStatus()).isEqualTo(TripStatus.SCHEDULED);
        assertThat(trip.getStartedAt()).isNull(); assertThat(trip.getEndedAt()).isNull();
        assertThat(trip.getScheduledDepartureAt()).isEqualTo(now);
        var archive=ArgumentCaptor.forClass(SimulationAttemptEntity.class);
        verify(attempts).saveAndFlush(archive.capture());
        assertThat(archive.getValue().getAttemptNumber()).isEqualTo(1);
        assertThat(archive.getValue().getStatus()).isEqualTo(status);
        assertThat(archive.getValue().getElapsedSeconds()).isEqualTo(15);
        assertThat(archive.getValue().getMultiplier()).isEqualTo(10);
        assertThat(state.getRevision()).isEqualTo(101); assertThat(state.getNextStopSequence()).isEqualTo(1);
        assertThat(state.isAwaitingExit()).isFalse(); assertThat(state.getAttemptNumber()).isEqualTo(2);
        assertThat(alert.getLastTriggerAt()).isNull(); assertThat(alert.getLastTrafficFetchedAt()).isNull();
        verify(revision).supersede(now);
        assertThat(service.reset(5L).attemptNumber()).isEqualTo(2);
        verify(attempts,times(1)).saveAndFlush(any());
        verifyNoInteractions(tripService,telemetry,positions);
        verify(runs,never()).saveAndFlush(any()); verify(trips,never()).saveAndFlush(any());
        verify(samples,never()).deleteAll();
    }

    @Test void rejectsGpsWithoutChangingState() {
        when(samples.existsByTripIdAndSource(5L,TelemetrySource.GPS)).thenReturn(true);
        rejects();
    }
    @Test void rejectsInactiveVehicleWithoutChangingState() {
        trip.getVehicle().deactivate(); rejects();
    }
    @Test void rejectsOtherActiveTripWithoutChangingState() {
        var other=new TripEntity(trip.getVehicle(),trip.getRoute(),now); ReflectionTestUtils.setField(other,"id",6L); other.start(now);
        when(trips.findAllByVehicleIdOrderByScheduledDepartureAtDescIdDesc(1L)).thenReturn(List.of(trip,other));
        rejects();
    }
    private void rejects() {
        assertThatThrownBy(()->service.reset(5L)).isInstanceOfSatisfying(ResponseStatusException.class,
            error->assertThat(error.getStatusCode().value()).isEqualTo(409));
        assertThat(trip.getAttemptNumber()).isEqualTo(1); assertThat(run.getElapsedSeconds()).isEqualTo(15);
        verifyNoInteractions(attempts,tripService,telemetry,checkpoints,alerts,revisions);
    }
}
