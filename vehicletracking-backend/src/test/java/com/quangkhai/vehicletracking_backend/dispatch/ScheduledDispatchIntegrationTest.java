package com.quangkhai.vehicletracking_backend.dispatch;

import com.quangkhai.vehicletracking_backend.auth.config.SecurityConfig.UserAccountPrincipal;
import com.quangkhai.vehicletracking_backend.auth.entity.UserAccountEntity;
import com.quangkhai.vehicletracking_backend.auth.entity.UserRole;
import com.quangkhai.vehicletracking_backend.auth.repository.UserAccountRepository;
import com.quangkhai.vehicletracking_backend.dispatch.entity.*;
import com.quangkhai.vehicletracking_backend.dispatch.repository.*;
import com.quangkhai.vehicletracking_backend.dispatch.service.*;
import com.quangkhai.vehicletracking_backend.driver.entity.DriverEntity;
import com.quangkhai.vehicletracking_backend.driver.repository.DriverRepository;
import com.quangkhai.vehicletracking_backend.route.repository.RouteRepository;
import com.quangkhai.vehicletracking_backend.reroute.entity.NotificationType;
import com.quangkhai.vehicletracking_backend.reroute.entity.TripNotificationEntity;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripNotificationRepository;
import com.quangkhai.vehicletracking_backend.schedule.entity.*;
import com.quangkhai.vehicletracking_backend.schedule.repository.TripScheduleRepository;
import com.quangkhai.vehicletracking_backend.schedule.service.TripScheduleService;
import com.quangkhai.vehicletracking_backend.schedule.dto.ScheduleUpsertRequest;
import com.quangkhai.vehicletracking_backend.simulation.SimulationFixtures;
import com.quangkhai.vehicletracking_backend.simulation.entity.SimulationStatus;
import com.quangkhai.vehicletracking_backend.simulation.repository.SimulationRepository;
import com.quangkhai.vehicletracking_backend.telemetry.service.OperationsSnapshotService;
import com.quangkhai.vehicletracking_backend.telemetry.entity.TelemetrySource;
import com.quangkhai.vehicletracking_backend.station.entity.StationEntity;
import com.quangkhai.vehicletracking_backend.station.repository.StationRepository;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.trip.service.TripService;
import com.quangkhai.vehicletracking_backend.trip.dto.TripCreateRequest;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import com.quangkhai.vehicletracking_backend.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.dao.DataIntegrityViolationException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.web.server.ResponseStatusException;

@Testcontainers
@SpringBootTest(properties = {"here.routing.enabled=false", "here.traffic.enabled=false",
        "trip-scheduling.enabled=false", "app.simulation.scheduling-enabled=false", "auth.security-enabled=true"})
@AutoConfigureMockMvc
class ScheduledDispatchIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final Instant DEPARTURE = Instant.parse("2026-10-01T08:00:00Z");
    @Autowired StationRepository stations;
    @Autowired RouteRepository routes;
    @Autowired VehicleRepository vehicles;
    @Autowired DriverRepository drivers;
    @Autowired UserAccountRepository accounts;
    @Autowired TripScheduleRepository schedules;
    @Autowired TripDispatchRepository dispatches;
    @Autowired TripRepository trips;
    @Autowired TripService tripService;
    @Autowired com.quangkhai.vehicletracking_backend.assignment.service.TripAssignmentService assignments;
    @Autowired TripScheduleService scheduleService;
    @Autowired com.quangkhai.vehicletracking_backend.driverportal.service.DriverNavigationService navigation;
    @Autowired com.quangkhai.vehicletracking_backend.simulation.service.SimulationService simulation;
    @Autowired org.springframework.context.ApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired DriverDispatchService driverDispatch;
    @Autowired DriverDispatchInboxRepository dispatchInbox;
    @Autowired SimulationRepository runs;
    @Autowired OperationsSnapshotService operationsSnapshot;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean Clock operationsClock;
    final AtomicReference<Instant> time = new AtomicReference<>(DEPARTURE.minusSeconds(60));

    record Fixture(long tripId, long scheduleId, long routeId, long vehicleId, long driverId,
                   UserAccountPrincipal principal) {}

    private TripCreateRequest onDemandRequest(long vehicleId, long routeId, Long driverId) {
        return new TripCreateRequest(vehicleId, routeId, driverId);
    }

    Fixture fixture() {
        when(operationsClock.instant()).thenAnswer(call -> time.get());
        int n = IDS.incrementAndGet();
        var a = stations.saveAndFlush(new StationEntity("A " + n, null,
                new BigDecimal("10.770000"), new BigDecimal("106.700000"), 50));
        var b = stations.saveAndFlush(new StationEntity("B " + n, null,
                new BigDecimal("10.771000"), new BigDecimal("106.701000"), 50));
        var route = routes.saveAndFlush(SimulationFixtures.route(a, b));
        var vehicle = vehicles.saveAndFlush(new VehicleEntity("DSP" + n, "Xe thử", null));
        var driver = drivers.saveAndFlush(new DriverEntity("Tài xế " + n, String.format("06%08d", n), "DSP-" + n));
        var account = accounts.saveAndFlush(new UserAccountEntity("dispatch-driver-" + n,
                "unused-test-password-hash", UserRole.DRIVER, driver));
        // Dispatch fixtures represent drivers who have already changed their initial password.
        account.changePassword(account.getPasswordHash());
        accounts.saveAndFlush(account);
        var schedule = new TripScheduleEntity("Lịch kiểm thử " + n, route, vehicle, driver,
                ScheduleFrequency.ONCE, LocalDate.of(2026, 10, 1), (short) 0,
                LocalTime.of(15, 0), "Asia/Ho_Chi_Minh", LocalDate.of(2026, 10, 1), null);
        schedules.saveAndFlush(schedule);
        var trip = tripService.createFromSchedule(schedule, DEPARTURE);
        var principal = mock(UserAccountPrincipal.class);
        when(principal.driverId()).thenReturn(driver.getId());
        when(principal.accountId()).thenReturn(account.getId());
        when(principal.getPassword()).thenReturn(account.getPasswordHash());
        return new Fixture(trip.trip().id(), schedule.getId(), route.getId(), vehicle.getId(),
                driver.getId(), principal);
    }

    private MockHttpSession session(UserAccountPrincipal principal, UserRole role) {
        var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(authentication));
        return session;
    }


    @Test void scheduleOccurrenceHasNoDispatchAndRemainsScheduledAfterDeparture() {
        var item = fixture();
        time.set(DEPARTURE.plusSeconds(3600));
        assertThat(dispatches.findById(item.tripId())).isEmpty();
        assertThat(runs.findByTripId(item.tripId())).isEmpty();
        assertThat(trips.findById(item.tripId()).orElseThrow().getStatus()).isEqualTo(TripStatus.SCHEDULED);
        assertThat(context.containsBean("tripDispatchPollingScheduler")).isFalse();
        assertThat(context.containsBean("tripDispatchJobService")).isFalse();
    }

    @Test void driverStartsFixedScheduleWithArchivedAutoConfirmation() {
        var item = fixture();
        archivedAuto(item);
        navigation.start(item.principal(), item.tripId());
        assertThat(trips.findById(item.tripId()).orElseThrow().getStatus()).isEqualTo(TripStatus.IN_PROGRESS);
        assertThat(runs.findByTripId(item.tripId()).orElseThrow().getStatus()).isEqualTo(SimulationStatus.RUNNING);
        assertThat(dispatches.findById(item.tripId()).orElseThrow().getState()).isEqualTo(DispatchState.CLOSED);
    }

    @Test void adminSimulatorStartsFixedScheduleAndReplayStillWorks() {
        var item = fixture();
        archivedAuto(item);
        simulation.play(item.tripId());
        simulation.stop(item.tripId());
        simulation.reset(item.tripId());
        simulation.play(item.tripId());
        assertThat(trips.findById(item.tripId()).orElseThrow().getAttemptNumber()).isEqualTo(2);
        assertThat(runs.findByTripId(item.tripId()).orElseThrow().getStatus()).isEqualTo(SimulationStatus.RUNNING);
    }

    @Test void ordinaryStartAllowsArchivedPolicyButStillNeedsDriver() {
        var item = fixture();
        archivedAuto(item);
        tripService.unassignDriver(item.tripId());
        assertThatThrownBy(() -> tripService.start(item.tripId())).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("gán tài xế");
        tripService.assignDriver(item.tripId(), item.driverId());
        assertThat(tripService.start(item.tripId()).trip().status()).isEqualTo(TripStatus.IN_PROGRESS);
    }

    @Test void ordinaryStartRejectsDriverRunningAnotherTrip() {
        var waiting = fixture();
        var other = fixture();
        long running = tripService.create(onDemandRequest(other.vehicleId(), other.routeId(), waiting.driverId())).trip().id();
        tripService.start(running);
        assertThatThrownBy(() -> tripService.start(waiting.tripId())).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Tài xế đang chạy");
        assertThat(runs.findByTripId(waiting.tripId())).isEmpty();
    }

    @Test void concurrentStartsSharingVehicleAllowOnlyOneTrip() throws Exception {
        var first = fixture();
        var second = fixture();
        long rival = tripService.create(onDemandRequest(first.vehicleId(), first.routeId(), second.driverId())).trip().id();
        var gate = new java.util.concurrent.CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var left = pool.submit(() -> attemptStart(first.tripId(), gate));
            var right = pool.submit(() -> attemptStart(rival, gate));
            gate.countDown();
            assertThat(List.of(left.get(15, TimeUnit.SECONDS), right.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(trips.findAllByVehicleIdAndStatusIn(first.vehicleId(), List.of(TripStatus.IN_PROGRESS))).hasSize(1);
    }

    @Test void concurrentStartsSharingDriverAllowOnlyOneTrip() throws Exception {
        var first = fixture();
        var second = fixture();
        long rival = tripService.create(onDemandRequest(second.vehicleId(), second.routeId(), first.driverId())).trip().id();
        var gate = new java.util.concurrent.CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var left = pool.submit(() -> attemptStart(first.tripId(), gate));
            var right = pool.submit(() -> attemptStart(rival, gate));
            gate.countDown();
            assertThat(List.of(left.get(15, TimeUnit.SECONDS), right.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(trips.findAllByDriverIdOrderByScheduledDepartureAtDescIdDesc(first.driverId()).stream()
                .filter(trip -> trip.getStatus() == TripStatus.IN_PROGRESS)).hasSize(1);
    }

    @Test void operationsBoardEndpointsAreRemovedWhileTripAndTelemetryRemainAvailable() throws Exception {
        var item = fixture();
        var account = accounts.saveAndFlush(new UserAccountEntity("board-retired-admin-" + IDS.incrementAndGet(),
                "unused-test-password-hash", UserRole.ADMIN, null));
        var principal = mock(UserAccountPrincipal.class);
        when(principal.accountId()).thenReturn(account.getId());
        when(principal.getPassword()).thenReturn(account.getPasswordHash());
        var admin = session(principal, UserRole.ADMIN);
        mvc.perform(get("/api/v1/operations-board").param("date", "2026-10-01").session(admin))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/operations-board/trips/{id}", item.tripId()).session(admin))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/trips/{id}", item.tripId()).session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.trip.id").value(item.tripId()));
        mvc.perform(get("/api/v1/telemetry/snapshot").session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.trips").isArray());
        assertThat(context.containsBean("operationsBoardService")).isFalse();
        assertThat(context.containsBean("operationsBoardRepository")).isFalse();
        assertThat(trips.findById(item.tripId()).orElseThrow().getStatus()).isEqualTo(TripStatus.SCHEDULED);
    }

    @Test void retiredDispatchEndpointsDoNotMutateTripOrSchedule() throws Exception {
        var item = fixture();
        var driver = session(item.principal(), UserRole.DRIVER);
        var adminAccount = accounts.saveAndFlush(new UserAccountEntity("retire-052-admin-" + IDS.incrementAndGet(),
                "unused-test-password-hash", UserRole.ADMIN, null));
        var admin = mock(UserAccountPrincipal.class);
        when(admin.accountId()).thenReturn(adminAccount.getId());
        when(admin.getPassword()).thenReturn(adminAccount.getPasswordHash());
        var adminSession = session(admin, UserRole.ADMIN);
        String csrf = mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN").getValue();
        for (String action : List.of("ready", "unavailable")) {
            mvc.perform(post("/api/v1/driver/trips/{id}/dispatch/" + action, item.tripId()).session(driver)
                    .cookie(new Cookie("XSRF-TOKEN", csrf)).header("X-XSRF-TOKEN", csrf)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":0,\"reason\":\"fixture\"}"))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/driver/trips/{id}/dispatch", item.tripId()).session(driver)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/trips/{id}/dispatch", item.tripId()).session(adminSession)).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/trips/{id}/dispatch/policy", item.tripId()).session(adminSession)
                .cookie(new Cookie("XSRF-TOKEN", csrf)).header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON).content("{\"startMode\":\"AUTO_IF_READY\",\"expectedRevision\":0}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/trips/{id}/dispatch/override-start", item.tripId()).session(adminSession)
                .cookie(new Cookie("XSRF-TOKEN", csrf)).header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"fixture\",\"expectedRevision\":0}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/schedules/{id}", item.scheduleId()).session(adminSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.startMode").doesNotExist());
        mvc.perform(get("/api/v1/driver/schedules").session(driver))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].startMode").doesNotExist());
        mvc.perform(get("/api/v1/trips/{id}", item.tripId()).session(adminSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.trip.dispatch").doesNotExist());
        assertThat(trips.findById(item.tripId()).orElseThrow().getStatus()).isEqualTo(TripStatus.SCHEDULED);
        assertThat(jdbc.queryForObject("select start_mode from vehicle_tracking.trip_schedules where id=?", String.class,
                item.scheduleId())).isEqualTo("MANUAL");
    }

    @Test void inboxFiltersRetiredScheduledNoticesBeforeLimitAndKeepsDirectUnassignment() {
        var item = fixture();
        long direct = tripService.create(onDemandRequest(item.vehicleId(), item.routeId(), null)).trip().id();
        dispatchInbox.saveAndFlush(new DriverDispatchInboxEntity(item.driverId(), direct, null,
                DriverInboxKind.TRIP_UNASSIGNED, "Đã thu hồi phân công", null, "052-keep-" + direct, DEPARTURE.minusSeconds(60)));
        for (var kind : List.of(DriverInboxKind.READY_WINDOW_OPEN, DriverInboxKind.TRIP_UNASSIGNED, DriverInboxKind.TRIP_STARTED)) {
            dispatchInbox.saveAndFlush(new DriverDispatchInboxEntity(item.driverId(), item.tripId(), null,
                    kind, "Thông báo cũ", null, "052-retired-" + item.tripId() + kind, DEPARTURE));
        }
        assertThat(driverDispatch.inbox(item.principal(), 1)).singleElement()
                .satisfies(notice -> assertThat(notice.tripId()).isEqualTo(direct));
    }

    @Test void directAcceptCannotTakeVehicleOfOverlappingOnDemandTrip() {
        Fixture first = fixture();
        Fixture second = fixture();
        tripService.cancel(first.tripId());
        tripService.cancel(second.tripId());
        var admin = accounts.saveAndFlush(new UserAccountEntity("assignment-050-" + IDS.incrementAndGet(),
                "unused-test-password-hash", UserRole.ADMIN, null));
        long firstDirect = tripService.create(onDemandRequest(first.vehicleId(), first.routeId(), null)).trip().id();
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
        var firstRequestId = tx.execute(status -> assignments.requestAssignment(
                trips.findById(firstDirect).orElseThrow(), first.driverId(), admin.getId()).getId());
        assignments.accept(first.principal(), firstRequestId);
        long secondDirect = tripService.create(onDemandRequest(first.vehicleId(), first.routeId(), null)).trip().id();
        var secondRequestId = tx.execute(status -> assignments.requestAssignment(
                trips.findById(secondDirect).orElseThrow(), second.driverId(), admin.getId()).getId());
        assertThatThrownBy(() -> assignments.accept(second.principal(), secondRequestId))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Tài xế hoặc xe đã có lịch xung đột");
        assertThat(trips.findById(firstDirect).orElseThrow().getDriver().getId()).isEqualTo(first.driverId());
        assertThat(trips.findById(secondDirect).orElseThrow().getDriver()).isNull();
    }


    private boolean attemptStart(long id, java.util.concurrent.CountDownLatch gate) throws Exception {
        gate.await(10, TimeUnit.SECONDS);
        try { tripService.start(id); return true; }
        catch (ResponseStatusException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(409);
            return false;
        }
    }

    private void archivedAuto(Fixture item) {
        jdbc.update("""
                insert into vehicle_tracking.trip_dispatches(trip_id,start_mode,state,primary_driver_id,
                    schedule_epoch,baseline_duration_seconds,created_at,updated_at,ready_driver_id,ready_vehicle_id,
                    ready_attempt_number,ready_assignment_revision,ready_at)
                values (?,'AUTO_IF_READY','CLOSED',?,0,60,now(),now(),?,?,1,0,now())
                """, item.tripId(), item.driverId(), item.driverId(), item.vehicleId());
    }
}
