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
import org.junit.jupiter.api.RepeatedTest;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.dao.DataIntegrityViolationException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.web.server.ResponseStatusException;

@Testcontainers
@SpringBootTest(properties = {"here.routing.enabled=false", "here.traffic.enabled=false",
        "trip-scheduling.enabled=false", "trip-dispatch.enabled=false", "app.simulation.scheduling-enabled=false",
        "auth.security-enabled=true"})
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
    @Autowired TripDispatchEventRepository events;
    @Autowired TripDispatchOfferRepository offers;
    @Autowired TripRepository trips;
    @Autowired TripService tripService;
    @Autowired TripScheduleService scheduleService;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean TripNotificationRepository notifications;
    @Autowired DriverDispatchService driverDispatch;
    @Autowired TripDispatchJobService job;
    @Autowired SimulationRepository runs;
    @Autowired OperationsSnapshotService operationsSnapshot;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoSpyBean DriverDispatchInboxRepository dispatchInbox;
    @MockitoBean Clock operationsClock;
    final AtomicReference<Instant> time = new AtomicReference<>(DEPARTURE.minusSeconds(60));

    record Fixture(long tripId, long scheduleId, long routeId, long vehicleId, long driverId,
                   UserAccountPrincipal principal, Long backupId, UserAccountPrincipal backupPrincipal) {}

    Fixture fixture(DispatchStartMode mode) {
        return fixture(mode, false);
    }

    Fixture fixture(DispatchStartMode mode, boolean withBackup) {
        return fixture(mode, withBackup, null);
    }

    Fixture fixture(DispatchStartMode mode, boolean withBackup, Fixture sharedBackup) {
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
        DriverEntity backup = null;
        UserAccountEntity backupAccount = null;
        if (sharedBackup != null) {
            backup = drivers.findById(sharedBackup.backupId()).orElseThrow();
        } else if (withBackup) {
            backup = drivers.saveAndFlush(new DriverEntity("Dự phòng " + n,
                    String.format("07%08d", n), "DSP-B-" + n));
            backupAccount = accounts.saveAndFlush(new UserAccountEntity("dispatch-backup-" + n,
                    "unused-test-password-hash", UserRole.DRIVER, backup));
        }
        var schedule = new TripScheduleEntity("Lịch kiểm thử " + n, route, vehicle, driver,
                ScheduleFrequency.ONCE, LocalDate.of(2026, 10, 1), (short) 0,
                LocalTime.of(15, 0), "Asia/Ho_Chi_Minh", LocalDate.of(2026, 10, 1), null);
        schedule.setDispatchPolicy(mode, withBackup, withBackup ? List.of(backup.getId()) : List.of());
        schedules.saveAndFlush(schedule);
        var trip = tripService.createFromSchedule(schedule, DEPARTURE);
        var principal = mock(UserAccountPrincipal.class);
        when(principal.driverId()).thenReturn(driver.getId());
        when(principal.accountId()).thenReturn(account.getId());
        when(principal.getPassword()).thenReturn(account.getPasswordHash());
        UserAccountPrincipal backupPrincipal = null;
        if (sharedBackup != null) {
            backupPrincipal = sharedBackup.backupPrincipal();
        } else if (withBackup) {
            backupPrincipal = mock(UserAccountPrincipal.class);
            when(backupPrincipal.driverId()).thenReturn(backup.getId());
            when(backupPrincipal.accountId()).thenReturn(backupAccount.getId());
            when(backupPrincipal.getPassword()).thenReturn(backupAccount.getPasswordHash());
        }
        return new Fixture(trip.trip().id(), schedule.getId(), route.getId(), vehicle.getId(),
                driver.getId(), principal, backup == null ? null : backup.getId(), backupPrincipal);
    }

    @Test void snapshotAndReadyKeepTripScheduledUntilJobStartsSimulator() {
        Fixture created = fixture(DispatchStartMode.AUTO_IF_READY);
        var dispatch = dispatches.findById(created.tripId()).orElseThrow();
        assertThat(dispatch.getStartMode()).isEqualTo(DispatchStartMode.AUTO_IF_READY);
        assertThat(dispatch.getState()).isEqualTo(DispatchState.WAITING_READY);
        assertThat(dispatch.getBaselineDurationSeconds()).isPositive();
        assertThat(events.findTop100ByDispatchTripIdOrderByCreatedAtDescIdDesc(created.tripId()))
                .extracting(TripDispatchEventEntity::getKind).containsExactly(DispatchEventKind.CREATED);

        var ready = driverDispatch.ready(created.principal(), created.tripId(), dispatch.getRevision());
        assertThat(ready.state()).isEqualTo(DispatchState.READY);
        assertThat(trips.findById(created.tripId()).orElseThrow().getStatus()).isEqualTo(TripStatus.SCHEDULED);
        assertThat(runs.findByTripId(created.tripId())).isEmpty();

        time.set(DEPARTURE);
        job.process(created.tripId());
        assertThat(trips.findById(created.tripId()).orElseThrow().getStatus()).isEqualTo(TripStatus.IN_PROGRESS);
        assertThat(runs.findByTripId(created.tripId()).orElseThrow().getStatus()).isEqualTo(SimulationStatus.RUNNING);
        assertThat(dispatches.findById(created.tripId()).orElseThrow().getState()).isEqualTo(DispatchState.STARTED);
        var adminView = operationsSnapshot.snapshot();
        assertThat(adminView.trips()).anyMatch(item -> item.id().equals(created.tripId())
                && item.status() == TripStatus.IN_PROGRESS && item.dispatch() != null
                && item.dispatch().state() == DispatchState.STARTED);
        assertThat(adminView.simulations()).anyMatch(item ->
                item.tripId() == created.tripId() && item.status() == SimulationStatus.RUNNING);
        assertThat(adminView.positions()).anyMatch(item ->
                item.tripId() == created.tripId() && item.source() == TelemetrySource.SIMULATOR);
    }

    @Test void separateAdminAndDriverSessionsSeeSameReadyAndStartedStateOverHttp() throws Exception {
        Fixture created = fixture(DispatchStartMode.AUTO_IF_READY);
        var adminAccount = accounts.saveAndFlush(new UserAccountEntity("dispatch-admin-http-" + IDS.incrementAndGet(),
                "unused-test-password-hash", UserRole.ADMIN, null));
        var admin = mock(UserAccountPrincipal.class);
        when(admin.accountId()).thenReturn(adminAccount.getId());
        when(admin.getPassword()).thenReturn(adminAccount.getPasswordHash());
        MockHttpSession driverSession = session(created.principal(), UserRole.DRIVER);
        MockHttpSession adminSession = session(admin, UserRole.ADMIN);
        long revision = dispatches.findById(created.tripId()).orElseThrow().getRevision();

        mvc.perform(get("/api/v1/trips/{id}/dispatch", created.tripId()).session(adminSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary.state").value("WAITING_READY"));
        mvc.perform(get("/api/v1/driver/trips/{id}/dispatch", created.tripId()).session(driverSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("WAITING_READY"));
        String csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk())
                .andReturn().getResponse().getCookie("XSRF-TOKEN").getValue();
        mvc.perform(post("/api/v1/driver/trips/{id}/dispatch/ready", created.tripId())
                        .session(driverSession).cookie(new Cookie("XSRF-TOKEN", csrf))
                        .header("X-XSRF-TOKEN", csrf).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("READY"));
        mvc.perform(get("/api/v1/trips/{id}/dispatch", created.tripId()).session(adminSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary.state").value("READY"));

        time.set(DEPARTURE);
        job.process(created.tripId());
        mvc.perform(get("/api/v1/trips/{id}/dispatch", created.tripId()).session(adminSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary.state").value("STARTED"));
        var snapshot = mvc.perform(get("/api/v1/telemetry/snapshot").session(adminSession))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var body = json.readTree(snapshot);
        assertThat(body.get("trips")).anyMatch(item -> item.get("id").asLong() == created.tripId()
                && item.get("status").asString().equals("IN_PROGRESS")
                && item.get("dispatch").get("state").asString().equals("STARTED"));
        assertThat(body.get("simulations")).anyMatch(item -> item.get("tripId").asLong() == created.tripId()
                && item.get("status").asString().equals("RUNNING"));
    }

    private MockHttpSession session(UserAccountPrincipal principal, UserRole role) {
        var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(authentication));
        return session;
    }

    @Test void manualScheduleSnapshotNeverAutoStarts() {
        Fixture created = fixture(DispatchStartMode.MANUAL);
        assertThat(dispatches.findById(created.tripId()).orElseThrow().getState()).isEqualTo(DispatchState.MANUAL);
        time.set(DEPARTURE);
        job.process(created.tripId());
        assertThat(trips.findById(created.tripId()).orElseThrow().getStatus()).isEqualTo(TripStatus.SCHEDULED);
        assertThat(runs.findByTripId(created.tripId())).isEmpty();
    }

    @Test void disablingAndEnablingScheduleInvalidatesReadyImmediately() {
        Fixture created = fixture(DispatchStartMode.AUTO_IF_READY);
        long revision = dispatches.findById(created.tripId()).orElseThrow().getRevision();
        driverDispatch.ready(created.principal(), created.tripId(), revision);
        scheduleService.disable(created.scheduleId());
        var paused = dispatches.findById(created.tripId()).orElseThrow();
        assertThat(paused.getState()).isEqualTo(DispatchState.ATTENTION);
        assertThat(paused.getAttentionCode()).isEqualTo(DispatchAttentionCode.SCHEDULE_DISABLED);
        assertThat(paused.getReadyAt()).isNull();
        assertThatThrownBy(() -> driverDispatch.ready(created.principal(), created.tripId(), revision))
                .isInstanceOf(ResponseStatusException.class);
        scheduleService.enable(created.scheduleId());
        var resumed = dispatches.findById(created.tripId()).orElseThrow();
        assertThat(resumed.getState()).isEqualTo(DispatchState.WAITING_READY);
        assertThat(resumed.getReadyAt()).isNull();
        assertThat(resumed.getScheduleEpoch())
                .isEqualTo(schedules.findById(created.scheduleId()).orElseThrow().getDispatchEpoch());
    }

    @Test void noBackupUnassignIsAttentionAndScheduleEpochStillResumes() {
        Fixture created = fixture(DispatchStartMode.AUTO_IF_READY);
        tripService.unassignDriver(created.tripId());
        var unassigned = dispatches.findById(created.tripId()).orElseThrow();
        assertThat(unassigned.getState()).isEqualTo(DispatchState.ATTENTION);
        assertThat(unassigned.getAttentionCode()).isEqualTo(DispatchAttentionCode.NO_BACKUP);
        assertThat(unassigned.getNextActionAt()).isNull();
        scheduleService.disable(created.scheduleId());
        assertThat(dispatches.findById(created.tripId()).orElseThrow().getAttentionCode())
                .isEqualTo(DispatchAttentionCode.SCHEDULE_DISABLED);
        scheduleService.enable(created.scheduleId());
        var resumed = dispatches.findById(created.tripId()).orElseThrow();
        assertThat(resumed.getAttentionCode()).isEqualTo(DispatchAttentionCode.NO_BACKUP);
        assertThat(resumed.getScheduleEpoch())
                .isEqualTo(schedules.findById(created.scheduleId()).orElseThrow().getDispatchEpoch());
    }

    @Test void legacyCreateCannotTakeAutoVehicleOrDriverReservation() {
        Fixture created = fixture(DispatchStartMode.AUTO_IF_READY);
        assertThatThrownBy(() -> tripService.create(new TripCreateRequest(created.vehicleId(), created.routeId(), null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("giữ chỗ");
        int n = IDS.incrementAndGet();
        var spareVehicle = vehicles.saveAndFlush(new VehicleEntity("DSPX" + n, "Xe khác", null));
        assertThatThrownBy(() -> tripService.create(new TripCreateRequest(spareVehicle.getId(),
                created.routeId(), created.driverId())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("giữ chỗ");
    }

    @Test void busyDriverLeadsToSequentialOfferAndBackupMustAcceptThenReady() {
        Fixture created = fixture(DispatchStartMode.AUTO_IF_READY, true);
        long revision = dispatches.findById(created.tripId()).orElseThrow().getRevision();
        driverDispatch.unavailable(created.principal(), created.tripId(), revision, "Bận việc đột xuất");
        assertThat(trips.findById(created.tripId()).orElseThrow().getDriver()).isNull();
        job.process(created.tripId());
        var pending = offers.findByDispatchTripIdAndStatus(created.tripId(), DispatchOfferStatus.PENDING).orElseThrow();
        assertThat(pending.getCandidateDriverId()).isEqualTo(created.backupId());
        assertThatThrownBy(() -> driverDispatch.detail(created.principal(), created.tripId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        assertThatThrownBy(() -> driverDispatch.detail(created.backupPrincipal(), created.tripId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        assertThatThrownBy(() -> driverDispatch.accept(created.principal(), pending.getId(),
                dispatches.findById(created.tripId()).orElseThrow().getRevision()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        var accepted = driverDispatch.accept(created.backupPrincipal(), pending.getId(),
                dispatches.findById(created.tripId()).orElseThrow().getRevision());
        assertThat(accepted.status()).isEqualTo(DispatchOfferStatus.ACCEPTED);
        assertThat(trips.findById(created.tripId()).orElseThrow().getDriver().getId()).isEqualTo(created.backupId());
        assertThat(dispatches.findById(created.tripId()).orElseThrow().getState()).isEqualTo(DispatchState.WAITING_READY);
        assertThat(dispatches.findById(created.tripId()).orElseThrow().getReadyAt()).isNull();
        driverDispatch.ready(created.backupPrincipal(), created.tripId(),
                dispatches.findById(created.tripId()).orElseThrow().getRevision());
        assertThat(dispatches.findById(created.tripId()).orElseThrow().getState()).isEqualTo(DispatchState.READY);
    }

    @Test void offerAtExactExpiryCannotBeAcceptedAndExhaustedPoolNeedsAdmin() {
        Fixture created = fixture(DispatchStartMode.AUTO_IF_READY, true);
        driverDispatch.unavailable(created.principal(), created.tripId(),
                dispatches.findById(created.tripId()).orElseThrow().getRevision(), "Không thể nhận chuyến");
        job.process(created.tripId());
        var pending = offers.findByDispatchTripIdAndStatus(created.tripId(), DispatchOfferStatus.PENDING).orElseThrow();
        time.set(pending.getExpiresAt());
        assertThatThrownBy(() -> driverDispatch.accept(created.backupPrincipal(), pending.getId(),
                dispatches.findById(created.tripId()).orElseThrow().getRevision()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("OFFER_EXPIRED");
        job.process(created.tripId());
        assertThat(offers.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(DispatchOfferStatus.EXPIRED);
        assertThat(dispatches.findById(created.tripId()).orElseThrow().getAttentionCode())
                .isEqualTo(DispatchAttentionCode.NO_BACKUP);
        assertThat(runs.findByTripId(created.tripId())).isEmpty();
    }

    @Test void clientWithoutPolicyCannotChangePrimaryToAConfiguredBackup() {
        Fixture created = fixture(DispatchStartMode.AUTO_IF_READY, true);
        var schedule = schedules.findById(created.scheduleId()).orElseThrow();
        var input = new ScheduleUpsertRequest(schedule.getName(), created.routeId(), created.vehicleId(),
                created.backupId(), schedule.getFrequency(), schedule.getScheduledDate(),
                schedule.getWeekdaysMask(), schedule.getDepartureTime(), schedule.getTimezone(),
                schedule.getEffectiveFrom(), schedule.getEffectiveUntil(), null, null, null);
        assertThatThrownBy(() -> scheduleService.update(created.scheduleId(), input))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("trùng tài xế chính");
        assertThat(schedules.findById(created.scheduleId()).orElseThrow().getDriver().getId())
                .isEqualTo(created.driverId());
    }

    @Test void dueWithoutReadyAlertsButLateReadyStartsBeforeCutoff() {
        Fixture created = fixture(DispatchStartMode.AUTO_IF_READY);
        time.set(DEPARTURE);
        job.process(created.tripId());
        assertThat(trips.findById(created.tripId()).orElseThrow().getStatus()).isEqualTo(TripStatus.SCHEDULED);
        assertThat(runs.findByTripId(created.tripId())).isEmpty();
        assertThat(dispatches.findById(created.tripId()).orElseThrow().getAttentionCode())
                .isEqualTo(DispatchAttentionCode.DRIVER_NOT_READY);
        time.set(DEPARTURE.plusSeconds(120));
        driverDispatch.ready(created.principal(), created.tripId(),
                dispatches.findById(created.tripId()).orElseThrow().getRevision());
        job.process(created.tripId());
        assertThat(runs.findByTripId(created.tripId()).orElseThrow().getStatus()).isEqualTo(SimulationStatus.RUNNING);
        assertThat(trips.findById(created.tripId()).orElseThrow().getStartedAt())
                .isEqualTo(DEPARTURE.plusSeconds(120));
    }

    @Test void cutoffNeverCatchesUpAndDatabaseRejectsInvalidDispatchState() {
        Fixture created = fixture(DispatchStartMode.AUTO_IF_READY);
        time.set(DEPARTURE.plusSeconds(15 * 60));
        job.process(created.tripId());
        assertThat(dispatches.findById(created.tripId()).orElseThrow().getAttentionCode())
                .isEqualTo(DispatchAttentionCode.WINDOW_EXPIRED);
        assertThat(runs.findByTripId(created.tripId())).isEmpty();
        assertThatThrownBy(() -> driverDispatch.ready(created.principal(), created.tripId(),
                dispatches.findById(created.tripId()).orElseThrow().getRevision()))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> jdbc.update(
                "update vehicle_tracking.trip_dispatches set state = 'BROKEN' where trip_id = ?", created.tripId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void concurrentJobCallsCreateOnlyOneRun() throws Exception {
        Fixture created = fixture(DispatchStartMode.AUTO_IF_READY);
        driverDispatch.ready(created.principal(), created.tripId(),
                dispatches.findById(created.tripId()).orElseThrow().getRevision());
        time.set(DEPARTURE);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> job.process(created.tripId()));
            var second = pool.submit(() -> job.process(created.tripId()));
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        }
        assertThat(trips.findById(created.tripId()).orElseThrow().getStatus()).isEqualTo(TripStatus.IN_PROGRESS);
        assertThat(runs.findByTripId(created.tripId())).isPresent();
        assertThat(events.findTop100ByDispatchTripIdOrderByCreatedAtDescIdDesc(created.tripId())
                .stream().filter(event -> event.getKind() == DispatchEventKind.AUTO_STARTED)).hasSize(1);
    }

    @Test void failedNotificationRollsBackTripAndRunThenRecordsStartFailure() {
        Fixture created = fixture(DispatchStartMode.AUTO_IF_READY);
        driverDispatch.ready(created.principal(), created.tripId(),
                dispatches.findById(created.tripId()).orElseThrow().getRevision());
        time.set(DEPARTURE);
        doThrow(new IllegalStateException("fixture notification failure")).when(notifications)
                .save(argThat((TripNotificationEntity item) -> item.getType() == NotificationType.TRIP_AUTO_STARTED));
        assertThatThrownBy(() -> job.process(created.tripId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fixture notification failure");
        assertThat(trips.findById(created.tripId()).orElseThrow().getStatus()).isEqualTo(TripStatus.SCHEDULED);
        assertThat(runs.findByTripId(created.tripId())).isEmpty();
        assertThat(dispatches.findById(created.tripId()).orElseThrow().getState()).isEqualTo(DispatchState.READY);
        job.markStartFailed(created.tripId());
        assertThat(dispatches.findById(created.tripId()).orElseThrow().getAttentionCode())
                .isEqualTo(DispatchAttentionCode.START_FAILED);
    }

    @Test void failedInboxWriteRollsBackAcceptAssignmentOfferAndEvent() {
        Fixture created = fixture(DispatchStartMode.AUTO_IF_READY, true);
        driverDispatch.unavailable(created.principal(), created.tripId(),
                dispatches.findById(created.tripId()).orElseThrow().getRevision(), "Bận đột xuất");
        job.process(created.tripId());
        var pending = offers.findByDispatchTripIdAndStatus(created.tripId(), DispatchOfferStatus.PENDING).orElseThrow();
        long revision = dispatches.findById(created.tripId()).orElseThrow().getRevision();
        long eventCount = events.findTop100ByDispatchTripIdOrderByCreatedAtDescIdDesc(created.tripId()).size();
        doThrow(new IllegalStateException("fixture inbox write failure")).when(dispatchInbox)
                .save(argThat((DriverDispatchInboxEntity item) -> item.getKind() == DriverInboxKind.TRIP_ASSIGNED));
        assertThatThrownBy(() -> driverDispatch.accept(created.backupPrincipal(), pending.getId(), revision))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fixture inbox write failure");
        assertThat(trips.findById(created.tripId()).orElseThrow().getDriver()).isNull();
        assertThat(offers.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(DispatchOfferStatus.PENDING);
        assertThat(dispatches.findById(created.tripId()).orElseThrow().getState()).isEqualTo(DispatchState.OFFER_PENDING);
        assertThat(dispatches.findById(created.tripId()).orElseThrow().getRevision()).isEqualTo(revision);
        assertThat(events.findTop100ByDispatchTripIdOrderByCreatedAtDescIdDesc(created.tripId())).hasSize((int) eventCount);
        assertThat(dispatchInbox.findByRecipientDriverIdOrderByCreatedAtDescIdDesc(created.backupId(),
                org.springframework.data.domain.PageRequest.of(0, 50))).noneMatch(
                        item -> item.getKind() == DriverInboxKind.TRIP_ASSIGNED);
    }

    @RepeatedTest(5) void twoTripsRacingForOneBackupCreateAtMostOnePendingOffer() throws Exception {
        Fixture first = fixture(DispatchStartMode.AUTO_IF_READY, true);
        Fixture second = fixture(DispatchStartMode.AUTO_IF_READY, true, first);
        driverDispatch.unavailable(first.principal(), first.tripId(),
                dispatches.findById(first.tripId()).orElseThrow().getRevision(), "Không nhận chuyến được");
        driverDispatch.unavailable(second.principal(), second.tripId(),
                dispatches.findById(second.tripId()).orElseThrow().getRevision(), "Không nhận chuyến được");
        CountDownLatch gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { gate.await(); job.process(first.tripId()); return null; });
            var b = pool.submit(() -> { gate.await(); job.process(second.tripId()); return null; });
            gate.countDown();
            a.get(20, TimeUnit.SECONDS);
            b.get(20, TimeUnit.SECONDS);
        }
        var pending = offers.findAllByCandidateDriverIdAndStatusOrderByExpiresAtAsc(
                first.backupId(), DispatchOfferStatus.PENDING);
        assertThat(pending).hasSize(1);
        assertThat(pending.getFirst().getDispatch().getTripId())
                .isIn(first.tripId(), second.tripId());
        var winnerTrip = pending.getFirst().getDispatch().getTripId();
        var accepted = driverDispatch.accept(first.backupPrincipal(), pending.getFirst().getId(),
                dispatches.findById(winnerTrip).orElseThrow().getRevision());
        assertThat(accepted.status()).isEqualTo(DispatchOfferStatus.ACCEPTED);
        assertThat(trips.findById(winnerTrip).orElseThrow().getDriver().getId()).isEqualTo(first.backupId());
        assertThat(trips.findById(winnerTrip == first.tripId() ? second.tripId() : first.tripId())
                .orElseThrow().getDriver()).isNull();
    }

    @RepeatedTest(3) void adminAssignmentCannotTakeDriverWhileOfferIsAccepted() throws Exception {
        Fixture automatic = fixture(DispatchStartMode.AUTO_IF_READY, true);
        Fixture manual = fixture(DispatchStartMode.MANUAL);
        driverDispatch.unavailable(automatic.principal(), automatic.tripId(),
                dispatches.findById(automatic.tripId()).orElseThrow().getRevision(), "Bận việc đột xuất");
        job.process(automatic.tripId());
        var pending = offers.findByDispatchTripIdAndStatus(automatic.tripId(), DispatchOfferStatus.PENDING).orElseThrow();
        long revision = dispatches.findById(automatic.tripId()).orElseThrow().getRevision();
        CountDownLatch gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var accept = pool.submit(() -> {
                gate.await();
                return driverDispatch.accept(automatic.backupPrincipal(), pending.getId(), revision);
            });
            var admin = pool.submit(() -> {
                gate.await();
                try {
                    tripService.assignDriver(manual.tripId(), automatic.backupId());
                    return false;
                } catch (ResponseStatusException conflict) {
                    return conflict.getStatusCode().value() == 409;
                }
            });
            gate.countDown();
            assertThat(accept.get(20, TimeUnit.SECONDS).status()).isEqualTo(DispatchOfferStatus.ACCEPTED);
            assertThat(admin.get(20, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(trips.findById(automatic.tripId()).orElseThrow().getDriver().getId())
                .isEqualTo(automatic.backupId());
        assertThat(trips.findById(manual.tripId()).orElseThrow().getDriver().getId())
                .isEqualTo(manual.driverId());
    }

    @RepeatedTest(3) void concurrentAcceptOfOneOfferAssignsDriverOnlyOnce() throws Exception {
        Fixture created = fixture(DispatchStartMode.AUTO_IF_READY, true);
        driverDispatch.unavailable(created.principal(), created.tripId(),
                dispatches.findById(created.tripId()).orElseThrow().getRevision(), "Bận việc đột xuất");
        job.process(created.tripId());
        var pending = offers.findByDispatchTripIdAndStatus(created.tripId(), DispatchOfferStatus.PENDING).orElseThrow();
        long revision = dispatches.findById(created.tripId()).orElseThrow().getRevision();
        CountDownLatch gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> acceptOnceAfterGate(created, pending.getId(), revision, gate));
            var second = pool.submit(() -> acceptOnceAfterGate(created, pending.getId(), revision, gate));
            gate.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(offers.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(DispatchOfferStatus.ACCEPTED);
        assertThat(trips.findById(created.tripId()).orElseThrow().getDriver().getId()).isEqualTo(created.backupId());
        assertThat(events.findTop100ByDispatchTripIdOrderByCreatedAtDescIdDesc(created.tripId())
                .stream().filter(item -> item.getKind() == DispatchEventKind.REASSIGNED)).hasSize(1);
        assertThat(dispatchInbox.findByRecipientDriverIdOrderByCreatedAtDescIdDesc(created.backupId(),
                org.springframework.data.domain.PageRequest.of(0, 50))
                .stream().filter(item -> item.getKind() == DriverInboxKind.TRIP_ASSIGNED)).hasSize(1);
    }

    private boolean acceptOnceAfterGate(Fixture created, java.util.UUID offerId, long revision,
                                        CountDownLatch gate) throws InterruptedException {
        gate.await();
        try {
            driverDispatch.accept(created.backupPrincipal(), offerId, revision);
            return true;
        } catch (ResponseStatusException conflict) {
            if (conflict.getStatusCode().value() != 409) throw conflict;
            return false;
        }
    }

    @Test void freshSchedulerUsesPersistedReadyAfterPauseButNeverStartsAfterCutoff() {
        Fixture starts = fixture(DispatchStartMode.AUTO_IF_READY);
        driverDispatch.ready(starts.principal(), starts.tripId(),
                dispatches.findById(starts.tripId()).orElseThrow().getRevision());
        time.set(DEPARTURE.plusSeconds(120));
        new TripDispatchPollingScheduler(dispatches, job, operationsClock).poll();
        assertThat(runs.findByTripId(starts.tripId())).isPresent();

        time.set(DEPARTURE.minusSeconds(60));
        Fixture expired = fixture(DispatchStartMode.AUTO_IF_READY);
        driverDispatch.ready(expired.principal(), expired.tripId(),
                dispatches.findById(expired.tripId()).orElseThrow().getRevision());
        time.set(DEPARTURE.plusSeconds(15 * 60));
        new TripDispatchPollingScheduler(dispatches, job, operationsClock).poll();
        assertThat(runs.findByTripId(expired.tripId())).isEmpty();
        assertThat(dispatches.findById(expired.tripId()).orElseThrow().getAttentionCode())
                .isEqualTo(DispatchAttentionCode.WINDOW_EXPIRED);
    }

    @Test void failedTripInPollingBatchDoesNotStopNextTrip() {
        Fixture failing = fixture(DispatchStartMode.AUTO_IF_READY);
        Fixture healthy = fixture(DispatchStartMode.AUTO_IF_READY);
        driverDispatch.ready(failing.principal(), failing.tripId(),
                dispatches.findById(failing.tripId()).orElseThrow().getRevision());
        driverDispatch.ready(healthy.principal(), healthy.tripId(),
                dispatches.findById(healthy.tripId()).orElseThrow().getRevision());
        time.set(DEPARTURE);
        doThrow(new IllegalStateException("fixture first trip failed")).when(notifications)
                .save(argThat((TripNotificationEntity item) -> item.getType() == NotificationType.TRIP_AUTO_STARTED
                        && item.getTrip().getId().equals(failing.tripId())));
        new TripDispatchPollingScheduler(dispatches, job, operationsClock).poll();
        assertThat(trips.findById(failing.tripId()).orElseThrow().getStatus()).isEqualTo(TripStatus.SCHEDULED);
        assertThat(runs.findByTripId(failing.tripId())).isEmpty();
        assertThat(dispatches.findById(failing.tripId()).orElseThrow().getAttentionCode())
                .isEqualTo(DispatchAttentionCode.START_FAILED);
        assertThat(trips.findById(healthy.tripId()).orElseThrow().getStatus()).isEqualTo(TripStatus.IN_PROGRESS);
        assertThat(runs.findByTripId(healthy.tripId()).orElseThrow().getStatus()).isEqualTo(SimulationStatus.RUNNING);
    }
}
