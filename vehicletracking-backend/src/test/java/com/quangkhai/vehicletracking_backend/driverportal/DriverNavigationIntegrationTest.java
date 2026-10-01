package com.quangkhai.vehicletracking_backend.driverportal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.quangkhai.vehicletracking_backend.auth.config.SecurityConfig.UserAccountPrincipal;
import com.quangkhai.vehicletracking_backend.driver.entity.DriverEntity;
import com.quangkhai.vehicletracking_backend.driver.repository.DriverRepository;
import com.quangkhai.vehicletracking_backend.driverportal.service.DriverNavigationService;
import com.quangkhai.vehicletracking_backend.reroute.entity.NotificationSeverity;
import com.quangkhai.vehicletracking_backend.reroute.entity.NotificationType;
import com.quangkhai.vehicletracking_backend.reroute.entity.RerouteReasonCode;
import com.quangkhai.vehicletracking_backend.reroute.entity.RouteRevisionStatus;
import com.quangkhai.vehicletracking_backend.reroute.entity.TripNotificationEntity;
import com.quangkhai.vehicletracking_backend.reroute.entity.TripRouteRevisionEntity;
import com.quangkhai.vehicletracking_backend.reroute.entity.TripRouteRevisionSectionEntity;
import com.quangkhai.vehicletracking_backend.reroute.entity.TripRouteRevisionStopEntity;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripNotificationRepository;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripRouteRevisionRepository;
import com.quangkhai.vehicletracking_backend.reroute.service.TripRouteGeometryQueryService;
import com.quangkhai.vehicletracking_backend.route.dto.RouteInstruction;
import com.quangkhai.vehicletracking_backend.route.error.RouteErrorCode;
import com.quangkhai.vehicletracking_backend.route.error.RouteOperationException;
import com.quangkhai.vehicletracking_backend.route.provider.CalculatedRoute;
import com.quangkhai.vehicletracking_backend.route.provider.CalculatedSection;
import com.quangkhai.vehicletracking_backend.route.provider.RoutingProvider;
import com.quangkhai.vehicletracking_backend.route.provider.RoutingWaypoint;
import com.quangkhai.vehicletracking_backend.route.repository.RouteRepository;
import com.quangkhai.vehicletracking_backend.simulation.SimulationFixtures;
import com.quangkhai.vehicletracking_backend.simulation.entity.SimulationStatus;
import com.quangkhai.vehicletracking_backend.simulation.repository.SimulationRepository;
import com.quangkhai.vehicletracking_backend.simulation.service.SimulationService;
import com.quangkhai.vehicletracking_backend.station.entity.StationEntity;
import com.quangkhai.vehicletracking_backend.station.repository.StationRepository;
import com.quangkhai.vehicletracking_backend.telemetry.service.OperationsSnapshotService;
import com.quangkhai.vehicletracking_backend.trip.dto.TripCreateRequest;
import com.quangkhai.vehicletracking_backend.trip.dto.TripDetailResponse;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import com.quangkhai.vehicletracking_backend.trip.service.TripService;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import com.quangkhai.vehicletracking_backend.vehicle.repository.VehicleRepository;

@Testcontainers
@SpringBootTest(properties = {"here.routing.enabled=false", "here.traffic.enabled=false",
        "app.simulation.scheduling-enabled=false", "trip-scheduling.enabled=false"})
class DriverNavigationIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");
    @Autowired DriverNavigationService navigation;
    @Autowired SimulationService simulator;
    @Autowired SimulationRepository runs;
    @Autowired TripService trips;
    @Autowired DriverRepository drivers;
    @Autowired StationRepository stations;
    @Autowired RouteRepository routes;
    @Autowired VehicleRepository vehicles;
    @Autowired TripRouteRevisionRepository revisions;
    @MockitoSpyBean TripNotificationRepository notifications;
    @Autowired com.quangkhai.vehicletracking_backend.trip.repository.TripRepository tripRepository;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired OperationsSnapshotService snapshots;
    @Autowired TripRouteGeometryQueryService routeQuery;
    @MockitoBean Clock operationsClock;
    @MockitoBean RoutingProvider routing;
    static final AtomicInteger IDS = new AtomicInteger();
    final AtomicReference<Instant> time = new AtomicReference<>();
    record Assigned(TripDetailResponse detail, UserAccountPrincipal principal) { long id() { return detail.trip().id(); } }

    @BeforeEach void setup() {
        time.set(Instant.now());
        when(operationsClock.instant()).thenAnswer(call -> time.get());
        when(routing.calculateAlternatives(anyList())).thenAnswer(call -> {
            List<RoutingWaypoint> wp = call.getArgument(0);
            var origin = wp.getFirst();
            var sections = new ArrayList<CalculatedSection>();
            for (int i = 1; i < wp.size(); i++) {
                var target = wp.get(i);
                sections.add(new CalculatedSection(i, i + 1, SimulationFixtures.encode(new double[][] {
                        {origin.latitude().doubleValue(), origin.longitude().doubleValue()},
                        {10.7703, 106.702}, {target.latitude().doubleValue(), target.longitude().doubleValue()}}),
                        400, 30, 30, List.of(new RouteInstruction("turn", "right", "Rẽ phải theo đường thử", 1))));
                origin = target;
            }
            return List.of(new CalculatedRoute(time.get(), sections));
        });
    }

    Assigned create() {
        return create(true);
    }
    Assigned create(boolean validGeometry) {
        int n = IDS.incrementAndGet();
        var a = stations.saveAndFlush(new StationEntity("Start " + n, null, new BigDecimal("10.770000"), new BigDecimal("106.700000"), 50));
        var b = stations.saveAndFlush(new StationEntity("Stop " + n, null, new BigDecimal("10.771000"), new BigDecimal("106.701000"), 50));
        var route = routes.saveAndFlush(validGeometry ? SimulationFixtures.route(a, b)
                : com.quangkhai.vehicletracking_backend.trip.TripFixtures.route(a, b));
        var vehicle = vehicles.saveAndFlush(new VehicleEntity("DN" + n, "Xe thử", null));
        var driver = drivers.saveAndFlush(new DriverEntity("Tài xế thử " + n, String.format("08%08d", n), "DN-LIC-" + n));
        var trip = trips.create(new TripCreateRequest(vehicle.getId(), route.getId(), driver.getId()));
        var principal = mock(UserAccountPrincipal.class); when(principal.driverId()).thenReturn(driver.getId());
        return new Assigned(trip, principal);
    }
    void conflict(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(409));
    }
    void notFound(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(404));
    }

    @Test void authenticatedAssignmentScopesEveryReadAndMutation() {
        var trip = create(); var other = create().principal();
        notFound(() -> navigation.navigation(other, trip.id()));
        notFound(() -> navigation.start(other, trip.id()));
        notFound(() -> navigation.options(other, trip.id()));
        notFound(() -> navigation.apply(other, trip.id(), UUID.randomUUID(), 0));
        assertThat(runs.findByTripId(trip.id())).isEmpty();
    }
    @Test void missingLinkedProfileIsForbidden() {
        var trip = create();
        var unlinked = mock(UserAccountPrincipal.class); when(unlinked.driverId()).thenReturn(null);
        assertThatThrownBy(() -> navigation.start(unlinked, trip.id()))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(403));
    }
    @Test void startAndRetryCreatesOneRunningTripVisibleToAdmin() {
        var trip = create(); var started = navigation.start(trip.principal(), trip.id());
        var again = navigation.start(trip.principal(), trip.id());
        assertThat(started.trip().status()).isEqualTo(TripStatus.IN_PROGRESS);
        assertThat(started.simulation().status()).isEqualTo(SimulationStatus.RUNNING);
        assertThat(again.simulation().id()).isEqualTo(started.simulation().id());
        assertThat(started.position().tripId()).isEqualTo(trip.id());
        assertThat(snapshots.snapshot().positions()).filteredOn(p -> p.tripId() == trip.id()).hasSize(1);
        assertThat(started.guidance()).isNull(); // Legacy geometry remains usable without fabricated instructions.
    }
    @Test void navigationSharesAdminCheckInsAndOnlyAssignedTripStationMetadata() {
        var assigned = create(); var unrelated = create();
        var waiting = navigation.navigation(assigned.principal(), assigned.id());
        var ids = assigned.detail().stops().stream().map(TripDetailResponse.Stop::stationId).distinct().toList();
        assertThat(waiting.stations()).extracting(s -> s.id()).containsExactlyInAnyOrderElementsOf(ids);
        assertThat(waiting.stations()).extracting(s -> s.id()).doesNotContain(
                unrelated.detail().stops().getFirst().stationId());
        assertThat(waiting.checkIns().visits()).isEmpty();
        var station = stations.findById(ids.getFirst()).orElseThrow();
        station.updateDetails(station.getName(), "Địa chỉ trạm cho tài xế", station.getLatitude(),
                station.getLongitude(), station.getCheckinRadiusMeters()); stations.saveAndFlush(station);
        navigation.start(assigned.principal(), assigned.id());
        time.updateAndGet(t -> t.plusSeconds(2)); simulator.tick(assigned.id());
        var driver = navigation.navigation(assigned.principal(), assigned.id());
        var admin = snapshots.snapshot();
        assertThat(driver.checkIns()).isEqualTo(admin.checkIns().stream()
                .filter(item -> item.tripId() == assigned.id()).findFirst().orElseThrow());
        assertThat(driver.stations()).anySatisfy(s -> assertThat(s.address()).isEqualTo("Địa chỉ trạm cho tài xế"));
        assertThat(driver.position()).isEqualTo(admin.positions().stream()
                .filter(item -> item.tripId() == assigned.id()).findFirst().orElseThrow());
    }
    @Test void startDoesNotResumeAdminPauseOrRestartFinishedTrip() {
        var trip = create(); navigation.start(trip.principal(), trip.id()); simulator.pause(trip.id());
        conflict(() -> navigation.start(trip.principal(), trip.id()));
        conflict(() -> navigation.options(trip.principal(), trip.id()));
        assertThat(runs.findByTripId(trip.id()).orElseThrow().getStatus()).isEqualTo(SimulationStatus.PAUSED);
        simulator.stop(trip.id()); conflict(() -> navigation.start(trip.principal(), trip.id()));
    }
    @Test void invalidGeometryRollsBackStartAndCreatesNoSimulation() {
        var trip = create(false);
        conflict(() -> navigation.start(trip.principal(), trip.id()));
        assertThat(trips.findById(trip.id()).trip().status()).isEqualTo(TripStatus.SCHEDULED);
        assertThat(runs.findByTripId(trip.id())).isEmpty();
    }
    @Test void assignedDriverCannotStartASecondConcurrentTrip() {
        var first = create();
        var second = trips.create(new TripCreateRequest(first.detail().trip().vehicleId(), first.detail().trip().routeId(), first.principal().driverId()));
        navigation.start(first.principal(), first.id());
        conflict(() -> navigation.start(first.principal(), second.trip().id()));
        assertThat(trips.findById(second.trip().id()).trip().status()).isEqualTo(TripStatus.SCHEDULED);
        assertThat(runs.findByTripId(second.trip().id())).isEmpty();
    }
    @Test void previewDoesNotChangeOfficialRouteOrNotifyAdmin() {
        var trip = create(); navigation.start(trip.principal(), trip.id());
        var before = routeQuery.get(trip.id());
        var options = navigation.options(trip.principal(), trip.id());
        assertThat(options.options()).hasSize(1);
        assertThat(revisions.countByTripId(trip.id())).isZero();
        assertThat(notifications.findAllByTripIdOrderByCreatedAtDescIdDesc(trip.id())).isEmpty();
        assertThat(routeQuery.get(trip.id())).isEqualTo(before);
    }
    @Test void applyPersistsGuidanceAndSharesExactRouteWithAdminWithoutTeleporting() {
        var trip = create(); navigation.start(trip.principal(), trip.id());
        var preview = navigation.options(trip.principal(), trip.id());
        time.updateAndGet(t -> t.plusSeconds(2)); simulator.tick(trip.id());
        var before = navigation.navigation(trip.principal(), trip.id());
        var changed = navigation.apply(trip.principal(), trip.id(), preview.token(), 0);
        assertThat(changed.routeRevisionId()).isNotNull();
        assertThat(changed.position().latitude()).isCloseTo(before.position().latitude(), within(.00001));
        assertThat(changed.position().longitude()).isCloseTo(before.position().longitude(), within(.00001));
        assertThat(changed.route()).isEqualTo(routeQuery.get(trip.id()));
        assertThat(changed.route().sections()).anySatisfy(s -> assertThat(s.instructions()).isNotEmpty());
        assertThat(changed.guidance().maneuver().instruction()).isEqualTo("Rẽ phải theo đường thử");
        var admin = snapshots.snapshot();
        assertThat(admin.simulations()).filteredOn(s -> s.tripId() == trip.id()).singleElement()
                .satisfies(s -> assertThat(s.routeRevisionId()).isEqualTo(changed.routeRevisionId()));
        assertThat(admin.notifications()).filteredOn(n -> n.tripId() == trip.id()).singleElement()
                .satisfies(n -> { assertThat(n.type()).isEqualTo(NotificationType.DRIVER_ROUTE_CHANGED); assertThat(n.reason()).contains("Tài xế thử"); });
        assertThat(changed.stops()).extracting(s -> s.sequenceNumber()).containsExactly(1, 2, 3);
        // Reload outside the write transaction to prove JSONB guidance is durable.
        assertThat(navigation.navigation(trip.principal(), trip.id()).guidance()).isEqualTo(changed.guidance());
        navigation.apply(trip.principal(), trip.id(), preview.token(), 0);
        assertThat(revisions.countByTripId(trip.id())).isEqualTo(1);
        assertThat(notifications.findAllByTripIdOrderByCreatedAtDescIdDesc(trip.id())).hasSize(1);
    }
    @Test void concurrentlyApplyingTheSamePreviewCreatesOneRevisionAndNotification() throws Exception {
        var trip = create(); navigation.start(trip.principal(), trip.id());
        var preview = navigation.options(trip.principal(), trip.id());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var first = pool.submit(() -> { gate.await(); return navigation.apply(trip.principal(), trip.id(), preview.token(), 0); });
            var second = pool.submit(() -> { gate.await(); return navigation.apply(trip.principal(), trip.id(), preview.token(), 0); });
            gate.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS).routeRevisionId()).isEqualTo(second.get(20, TimeUnit.SECONDS).routeRevisionId());
        }
        assertThat(revisions.countByTripId(trip.id())).isEqualTo(1);
        assertThat(notifications.findAllByTripIdOrderByCreatedAtDescIdDesc(trip.id())).hasSize(1);
    }
    @Test void expiredUnknownOrOldPreviewKeepsOriginalRoute() {
        var trip = create(); navigation.start(trip.principal(), trip.id());
        var old = navigation.options(trip.principal(), trip.id());
        var latest = navigation.options(trip.principal(), trip.id());
        conflict(() -> navigation.apply(trip.principal(), trip.id(), old.token(), 0));
        time.updateAndGet(t -> t.plusSeconds(121));
        conflict(() -> navigation.apply(trip.principal(), trip.id(), latest.token(), 0));
        conflict(() -> navigation.apply(trip.principal(), trip.id(), UUID.randomUUID(), 0));
        assertThat(revisions.countByTripId(trip.id())).isZero();
    }
    @Test void foreignPreviewAndInvalidIndexAreRejected() {
        var a = create(); var b = create(); navigation.start(a.principal(), a.id()); navigation.start(b.principal(), b.id());
        var preview = navigation.options(a.principal(), a.id());
        notFound(() -> navigation.apply(b.principal(), b.id(), preview.token(), 0));
        assertThatThrownBy(() -> navigation.apply(a.principal(), a.id(), preview.token(), 2))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
    }
    @Test void changedAttemptOrPauseInvalidatesUncommittedChoice() {
        var trip = create(); navigation.start(trip.principal(), trip.id());
        var options = navigation.options(trip.principal(), trip.id());
        simulator.pause(trip.id()); conflict(() -> navigation.apply(trip.principal(), trip.id(), options.token(), 0));
        simulator.reset(trip.id()); simulator.play(trip.id());
        conflict(() -> navigation.apply(trip.principal(), trip.id(), options.token(), 0));
        assertThat(revisions.countByTripId(trip.id())).isZero();
    }
    @Test void changedRouteVersionRejectsPreview() {
        var trip = create(); navigation.start(trip.principal(), trip.id());
        var first = navigation.options(trip.principal(), trip.id());
        navigation.apply(trip.principal(), trip.id(), first.token(), 0);
        var second = navigation.options(trip.principal(), trip.id());
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var entity = tripRepository.findLockedById(trip.id()).orElseThrow();
            var previous = revisions.findTopByTripIdAndStatusOrderByRevisionNumberDesc(trip.id(), RouteRevisionStatus.ACTIVE).orElseThrow();
            var replacement = new TripRouteRevisionEntity(entity, entity.getRoute(), 2, RerouteReasonCode.ROAD_CLOSURE,
                    "Auto-reroute", null, NotificationSeverity.CRITICAL, 60, 60, time.get());
            for (var section : previous.getSections()) replacement.addSection(new TripRouteRevisionSectionEntity(section.getSectionSequence(),
                    section.getDestinationStopSequence(), section.getEncodedPolyline(), section.getDistanceMeters(),
                    section.getTravelDurationSeconds(), section.getBaseTravelDurationSeconds(), section.getInstructions()));
            for (var stop : previous.getStops()) replacement.addStop(new TripRouteRevisionStopEntity(stop.getOriginalStopSequence(), stop.getSequenceNumber(),
                    stop.getStationId(), stop.getStationName(), stop.getLatitude(), stop.getLongitude(), stop.getDwellDurationSeconds(),
                    stop.getBaselineArrivalAt(), stop.getBaselineDepartureAt(), stop.getRevisedArrivalAt(), stop.getRevisedDepartureAt()));
            previous.supersede(time.get()); revisions.saveAndFlush(previous); revisions.saveAndFlush(replacement);
        });
        simulator.refreshRoute(trip.id());
        conflict(() -> navigation.apply(trip.principal(), trip.id(), second.token(), 0));
        assertThat(navigation.navigation(trip.principal(), trip.id()).routeRevisionId()).isNotEqualTo(second.routeRevisionId());
    }
    @Test void notificationFailureRollsBackRevisionAndTelemetryTogether() {
        var trip = create(); navigation.start(trip.principal(), trip.id());
        var preview = navigation.options(trip.principal(), trip.id());
        doThrow(new IllegalStateException("Notification storage unavailable")).when(notifications).saveAndFlush(any(TripNotificationEntity.class));
        assertThatThrownBy(() -> navigation.apply(trip.principal(), trip.id(), preview.token(), 0)).isInstanceOf(IllegalStateException.class);
        assertThat(revisions.countByTripId(trip.id())).isZero();
        assertThat(navigation.navigation(trip.principal(), trip.id()).routeRevisionId()).isNull();
        assertThat(runs.findByTripId(trip.id()).orElseThrow().getStatus()).isEqualTo(SimulationStatus.RUNNING);
        assertThat(notifications.findAllByTripIdOrderByCreatedAtDescIdDesc(trip.id())).isEmpty();
    }
    @Test void providerFailureIsControlledAndLeavesTripRunningOnOldRoute() {
        var trip = create(); navigation.start(trip.principal(), trip.id());
        doThrow(new RouteOperationException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                RouteErrorCode.ROUTING_PROVIDER_UNAVAILABLE, "provider unavailable")).when(routing).calculateAlternatives(anyList());
        assertThatThrownBy(() -> navigation.options(trip.principal(), trip.id()))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
        assertThat(revisions.countByTripId(trip.id())).isZero();
        assertThat(runs.findByTripId(trip.id()).orElseThrow().getStatus()).isEqualTo(SimulationStatus.RUNNING);
    }
    @Test void providerCannotOmitMandatoryStops() {
        var trip = create(); navigation.start(trip.principal(), trip.id());
        doReturn(List.of(new CalculatedRoute(time.get(), List.of(
                new CalculatedSection(1, 2, SimulationFixtures.encode(new double[][]{{10.77, 106.70}, {10.78, 106.71}}), 2000, 300, 300)))))
                .when(routing).calculateAlternatives(anyList());
        assertThat(navigation.options(trip.principal(), trip.id()).options()).isEmpty();
        assertThat(revisions.countByTripId(trip.id())).isZero();
    }
    @Test void vehicleMovingAwayFromPreviewCannotJumpBackToChosenRoad() {
        var trip = create(); navigation.start(trip.principal(), trip.id());
        var options = navigation.options(trip.principal(), trip.id());
        time.updateAndGet(t -> t.plusSeconds(15)); simulator.tick(trip.id());
        var before = navigation.navigation(trip.principal(), trip.id());
        conflict(() -> navigation.apply(trip.principal(), trip.id(), options.token(), 0));
        assertThat(navigation.navigation(trip.principal(), trip.id()).route()).isEqualTo(before.route());
        assertThat(revisions.countByTripId(trip.id())).isZero();
    }
    @Test void dwellingInvalidatesChoiceWithoutSkippingTheStation() {
        var trip = create(); navigation.start(trip.principal(), trip.id());
        var options = navigation.options(trip.principal(), trip.id());
        time.updateAndGet(t -> t.plusSeconds(21)); simulator.tick(trip.id());
        conflict(() -> navigation.apply(trip.principal(), trip.id(), options.token(), 0));
        conflict(() -> navigation.options(trip.principal(), trip.id()));
        assertThat(revisions.countByTripId(trip.id())).isZero();
    }
}
