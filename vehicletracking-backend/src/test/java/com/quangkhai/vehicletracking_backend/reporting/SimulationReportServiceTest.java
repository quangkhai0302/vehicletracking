package com.quangkhai.vehicletracking_backend.reporting;
import com.quangkhai.vehicletracking_backend.reporting.dto.*;
import com.quangkhai.vehicletracking_backend.reporting.service.SimulationReportService;
import com.quangkhai.vehicletracking_backend.simulation.entity.*;
import com.quangkhai.vehicletracking_backend.simulation.repository.*;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import com.quangkhai.vehicletracking_backend.trip.TripFixtures;
import com.quangkhai.vehicletracking_backend.simulation.SimulationFixtures;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import com.quangkhai.vehicletracking_backend.reroute.entity.*;
import com.quangkhai.vehicletracking_backend.reroute.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimulationReportServiceTest {
    final Instant now=Instant.parse("2026-10-05T17:00:00Z");
    SimulationRepository runs=mock(SimulationRepository.class);
    SimulationAttemptRepository attempts=mock(SimulationAttemptRepository.class);
    TripRepository trips=mock(TripRepository.class);
    TripNotificationRepository notifications=mock(TripNotificationRepository.class);
    TripRouteRevisionRepository revisions=mock(TripRouteRevisionRepository.class);
    SimulationReportService service=new SimulationReportService(runs,attempts,trips,notifications,revisions,Clock.fixed(now,ZoneOffset.UTC));
    TripEntity trip; SimulationRunEntity current; SimulationAttemptEntity archived;
    @BeforeEach void fixture() {
        var vehicle=new VehicleEntity("SIM123","Fixture",null); ReflectionTestUtils.setField(vehicle,"id",1L);
        var route=SimulationFixtures.route(TripFixtures.station("A"),TripFixtures.station("B"));
        ReflectionTestUtils.setField(route,"id",2L);
        trip=new TripEntity(vehicle,route,now.minusSeconds(100)); ReflectionTestUtils.setField(trip,"id",3L);
        trip.start(now.minusSeconds(100));
        current=new SimulationRunEntity(3L,now.minusSeconds(100));
        current.captureFirstPlay(SimulationAttemptMetadata.capture(trip,now.minusSeconds(100),44,312));
        current.addVirtualSeconds(84); current.advance(44,now.minusSeconds(1));
        current.changeScenario(SimulationScenario.CONGESTION);
        current.changeStatus(SimulationStatus.COMPLETED,now.minusSeconds(1)); trip.complete(now.minusSeconds(1));
        archived=new SimulationAttemptEntity(trip,current,now);
        trip.replay(now); trip.start(now);
        current.replay(now); current.captureFirstPlay(SimulationAttemptMetadata.capture(trip,now,44,312));
        current.addVirtualSeconds(44); current.advance(44,now);
        current.changeStatus(SimulationStatus.COMPLETED,now); trip.complete(now);
        when(runs.findForSimulationReport(any(),any(),any(),any())).thenReturn(List.of(current));
        when(attempts.findForSimulationReport(any(),any(),any(),any())).thenReturn(List.of(archived));
        when(trips.findAllById(any())).thenReturn(List.of(trip));
    }
    @Test void archivedAndCurrentAreCountedOnceAndMetricDoesNotChangeSummary() {
        var notification=new TripNotificationEntity(trip,null,NotificationType.OFF_ROUTE_DETECTED,NotificationSeverity.MAJOR,
            "Off route","Fixture",null,"",null,null,"test",now);
        notification.attributeSimulation(1); notification.markRead(now); notification.dismiss(now);
        when(notifications.findSimulationOffRouteEvents(any())).thenReturn(List.of(notification));
        var revision=mock(TripRouteRevisionEntity.class);
        when(revision.getTrip()).thenReturn(trip); when(revision.getSimulationAttemptNumber()).thenReturn(1);
        when(revision.getId()).thenReturn(9L); when(revision.getRevisionNumber()).thenReturn(1);
        when(revisions.findAllForSimulationReport(any())).thenReturn(List.of(revision));
        var all=service.report(null,null,null,null,SimulationReportMetric.ALL,0,20);
        assertThat(all.attemptCount()).isEqualTo(2); assertThat(all.completedAttemptCount()).isEqualTo(2);
        assertThat(all.knownCompletedAttemptCount()).isEqualTo(2); assertThat(all.onTimeRatePercent()).isEqualTo(50);
        assertThat(all.totalVirtualSeconds()).isEqualTo(128); assertThat(all.totalPlannedDistanceMeters()).isEqualTo(624);
        assertThat(all.offRouteEventCount()).isEqualTo(1); assertThat(all.lateAttemptCount()).isEqualTo(1);
        assertThat(all.items()).extracting(SimulationReportItem::attemptNumber).containsExactly(2,1);
        assertThat(all.items().getFirst().routeRevisions()).isEmpty();
        assertThat(all.items().getLast().routeRevisions()).extracting(SimulationReportRevision::revisionId).containsExactly(9L);
        var late=service.report(null,null,null,null,SimulationReportMetric.LATE,0,1);
        assertThat(late.attemptCount()).isEqualTo(2); assertThat(late.onTimeRatePercent()).isEqualTo(50);
        assertThat(late.totalElements()).isEqualTo(1); assertThat(late.items()).singleElement().satisfies(item -> {
            assertThat(item.attemptNumber()).isEqualTo(1); assertThat(item.current()).isFalse();
            assertThat(item.latenessSeconds()).isEqualTo(40);
        });
        assertThat(service.report(null,null,null,null,SimulationReportMetric.LATE,Integer.MAX_VALUE,100).items()).isEmpty();
    }
    @Test void defaultRangeAndExplicitDateBoundariesUseVietnamMidnight() {
        var report=service.report(null,null,1L,null,SimulationReportMetric.ALL,0,20);
        assertThat(report.to()).isEqualTo(LocalDate.of(2026,10,6));
        assertThat(report.from()).isEqualTo(LocalDate.of(2026,9,7));
        verify(runs).findForSimulationReport(Instant.parse("2026-09-06T17:00:00Z"),Instant.parse("2026-10-06T17:00:00Z"),1L,null);
    }
    @Test void legacyClockIsUnknownAndProgressNeverBecomesRuntimeOrPunctuality() {
        ReflectionTestUtils.setField(current,"metadata",null); ReflectionTestUtils.setField(current,"virtualElapsedSeconds",null);
        when(attempts.findForSimulationReport(any(),any(),any(),any())).thenReturn(List.of());
        var report=service.report(null,null,null,null,SimulationReportMetric.ALL,0,20);
        assertThat(report.attemptCount()).isEqualTo(1); assertThat(report.unknownAttemptCount()).isEqualTo(1);
        assertThat(report.totalVirtualSeconds()).isZero(); assertThat(report.onTimeRatePercent()).isNull();
        assertThat(report.items()).singleElement().satisfies(item -> {
            assertThat(item.punctuality()).isEqualTo(SimulationPunctuality.UNKNOWN);
            assertThat(item.progressSeconds()).isEqualTo(44);
            assertThat(item.virtualElapsedSeconds()).isNull(); assertThat(item.vehicleId()).isNull();
        });
    }
    @Test void stoppedAttemptsDoNotCountAsLateOrOnTimeButOngoingBlockedCanBeLate() {
        current.changeStatus(SimulationStatus.STOPPED,now);
        var report=service.report(null,null,null,null,SimulationReportMetric.ALL,0,20);
        assertThat(report.completedAttemptCount()).isEqualTo(1);
        assertThat(report.items().getFirst().punctuality()).isEqualTo(SimulationPunctuality.NOT_COMPLETED);
        current.addVirtualSeconds(1); current.changeStatus(SimulationStatus.RUNNING,now);
        assertThat(service.report(null,null,null,null,SimulationReportMetric.LATE,0,20).totalElements()).isEqualTo(2);
    }
    @Test void invalidFiltersAndRangeAreRejectedBeforeQuerying() {
        assertThatThrownBy(() -> service.report(LocalDate.of(2026,1,2),LocalDate.of(2026,1,1),null,null,SimulationReportMetric.ALL,0,20)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.report(LocalDate.of(2025,1,1),LocalDate.of(2026,1,2),null,null,SimulationReportMetric.ALL,0,20)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.report(null,null,0L,null,SimulationReportMetric.ALL,0,20)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.report(null,null,null,null,SimulationReportMetric.ALL,-1,20)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.report(null,null,null,null,SimulationReportMetric.ALL,0,101)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(notifications,revisions);
    }
}

