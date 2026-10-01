package com.quangkhai.vehicletracking_backend.assignment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.quangkhai.vehicletracking_backend.assignment.entity.TripAssignmentRequestEntity;
import com.quangkhai.vehicletracking_backend.assignment.entity.TripAssignmentStatus;
import com.quangkhai.vehicletracking_backend.assignment.repository.TripAssignmentRequestRepository;
import com.quangkhai.vehicletracking_backend.auth.config.SecurityConfig.UserAccountPrincipal;
import com.quangkhai.vehicletracking_backend.auth.entity.UserAccountEntity;
import com.quangkhai.vehicletracking_backend.auth.entity.UserRole;
import com.quangkhai.vehicletracking_backend.auth.repository.UserAccountRepository;
import com.quangkhai.vehicletracking_backend.dispatch.entity.DriverDispatchInboxEntity;
import com.quangkhai.vehicletracking_backend.dispatch.entity.DriverInboxKind;
import com.quangkhai.vehicletracking_backend.dispatch.repository.DriverDispatchInboxRepository;
import com.quangkhai.vehicletracking_backend.dispatch.service.DispatchAvailabilityService;
import com.quangkhai.vehicletracking_backend.driver.entity.DriverEntity;
import com.quangkhai.vehicletracking_backend.driver.repository.DriverRepository;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripNotificationRepository;
import com.quangkhai.vehicletracking_backend.route.entity.RouteEntity;
import com.quangkhai.vehicletracking_backend.trip.TripFixtures;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;

@ExtendWith(MockitoExtension.class)
class TripAssignmentServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-01T08:00:00Z");

    @Mock TripAssignmentRequestRepository requests;
    @Mock TripRepository trips;
    @Mock DriverRepository drivers;
    @Mock UserAccountRepository accounts;
    @Mock DriverDispatchInboxRepository inbox;
    @Mock TripNotificationRepository notifications;
    @Mock DispatchAvailabilityService availability;

    private TripAssignmentService service;
    private VehicleEntity vehicle;
    private RouteEntity route;
    private DriverEntity driverA;
    private DriverEntity driverB;
    private UserAccountEntity admin;
    private TripEntity trip;

    @BeforeEach
    void setUp() {
        service = new TripAssignmentService(requests, trips, drivers, accounts, inbox, notifications,
                availability, Clock.fixed(NOW, ZoneOffset.UTC));
        vehicle = new VehicleEntity("51B12345", "Xe A", null);
        ReflectionTestUtils.setField(vehicle, "id", 3L);
        var a = TripFixtures.station("A");
        var b = TripFixtures.station("B");
        ReflectionTestUtils.setField(a, "id", 11L);
        ReflectionTestUtils.setField(b, "id", 12L);
        route = TripFixtures.route(a, b);
        ReflectionTestUtils.setField(route, "id", 7L);
        driverA = driver("Nguyễn Văn A", 21L);
        driverB = driver("Nguyễn Văn B", 22L);
        admin = new UserAccountEntity("admin.fixture", "hash", UserRole.ADMIN, null);
        ReflectionTestUtils.setField(admin, "id", 31L);
        trip = new TripEntity(vehicle, route, NOW.plusSeconds(3600));
        ReflectionTestUtils.setField(trip, "id", 101L);
        lenient().when(inbox.existsByDedupeKey(anyString())).thenReturn(false);
    }

    @Test
    void replacementCancelsAndFlushesPendingBeforeInsertingReplacement() {
        var oldRequest = request(driverA);
        when(drivers.findLockedById(driverB.getId())).thenReturn(Optional.of(driverB));
        when(accounts.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(accounts.existsActiveDriverAccount(driverB.getId())).thenReturn(true);
        when(requests.findFirstByTripIdAndStatusOrderByRequestedAtDescIdDesc(trip.getId(), TripAssignmentStatus.PENDING))
                .thenReturn(Optional.of(oldRequest));
        when(requests.findAllByCandidateDriverIdAndStatusOrderByRequestedAtDescIdDesc(eq(driverB.getId()),
                eq(TripAssignmentStatus.PENDING), any())).thenReturn(List.of());
        when(availability.driverReservedForAuto(anyLong(), any(), anyLong(), eq(trip.getId()))).thenReturn(false);
        when(availability.driverAvailable(eq(driverB.getId()), eq(trip), anyLong(), eq(false))).thenReturn(true);
        when(requests.saveAndFlush(any(TripAssignmentRequestEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var replacement = service.requestAssignment(trip, driverB.getId(), admin.getId());

        assertThat(oldRequest.getStatus()).isEqualTo(TripAssignmentStatus.CANCELLED);
        assertThat(replacement.getStatus()).isEqualTo(TripAssignmentStatus.PENDING);
        InOrder order = inOrder(requests);
        order.verify(requests).flush();
        order.verify(requests).saveAndFlush(any(TripAssignmentRequestEntity.class));
        verify(inbox).save(argThat(item -> item.getKind() == DriverInboxKind.DIRECT_ASSIGNMENT_REQUESTED
                && item.getRecipientDriverId() == driverB.getId()
                && item.getAssignmentRequestId().equals(replacement.getId())));
    }

    @Test
    void acceptChecksVehicleOverlapIncludingOnDemandTripsAndPersistsAtomically() {
        var request = request(driverA);
        var principal = principal(driverA.getId());
        when(requests.findById(request.getId())).thenReturn(Optional.of(request));
        when(trips.findLockedById(trip.getId())).thenReturn(Optional.of(trip));
        when(drivers.findLockedById(driverA.getId())).thenReturn(Optional.of(driverA));
        when(requests.findLockedById(request.getId())).thenReturn(Optional.of(request));
        when(accounts.existsActiveDriverAccount(driverA.getId())).thenReturn(true);
        when(availability.driverReservedForAuto(driverA.getId(), trip.getScheduledDepartureAt(),
                route.getEstimatedTripDurationSeconds(), trip.getId())).thenReturn(false);
        when(availability.driverAvailable(driverA.getId(), trip, route.getEstimatedTripDurationSeconds(), true))
                .thenReturn(true);
        when(availability.vehicleAvailable(vehicle.getId(), trip, route.getEstimatedTripDurationSeconds(), true))
                .thenReturn(true);

        var result = service.accept(principal, request.getId());

        assertThat(result.status()).isEqualTo(TripAssignmentStatus.ACCEPTED);
        assertThat(trip.getDriver()).isSameAs(driverA);
        verify(availability).vehicleAvailable(vehicle.getId(), trip, route.getEstimatedTripDurationSeconds(), true);
        verify(trips).flush();
        verify(inbox).save(argThat(item -> item.getKind() == DriverInboxKind.DIRECT_ASSIGNMENT_ACCEPTED
                && item.getAssignmentRequestId().equals(request.getId())));
    }

    @Test
    void declineTrimsReasonAndNotifiesAdmin() {
        var request = request(driverA);
        var principal = principal(driverA.getId());
        when(requests.findById(request.getId())).thenReturn(Optional.of(request));
        when(trips.findLockedById(trip.getId())).thenReturn(Optional.of(trip));
        when(drivers.findLockedById(driverA.getId())).thenReturn(Optional.of(driverA));
        when(requests.findLockedById(request.getId())).thenReturn(Optional.of(request));

        var result = service.decline(principal, request.getId(), "  Không phù hợp lịch chạy  ");

        assertThat(result.status()).isEqualTo(TripAssignmentStatus.DECLINED);
        assertThat(request.getResponseReason()).isEqualTo("Không phù hợp lịch chạy");
        verify(notifications).save(any());
        verifyNoInteractions(availability);
    }

    @Test
    void declineRejectsReasonOutsideContractBeforeMutation() {
        var request = request(driverA);
        var principal = principal(driverA.getId());
        when(requests.findById(request.getId())).thenReturn(Optional.of(request));
        when(trips.findLockedById(trip.getId())).thenReturn(Optional.of(trip));
        when(drivers.findLockedById(driverA.getId())).thenReturn(Optional.of(driverA));
        when(requests.findLockedById(request.getId())).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.decline(principal, request.getId(), " no "))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode().value()).isEqualTo(400);
                    assertThat(error.getBody().getProperties().get("code")).isEqualTo("ASSIGNMENT_VALIDATION_FAILED");
                });
        assertThat(request.getStatus()).isEqualTo(TripAssignmentStatus.PENDING);
        verifyNoInteractions(notifications);
    }

    @Test
    void unassignUsesAcceptedRequestIdInDedupeKeyForNewAssignmentEpisode() {
        trip.assignDriver(driverA);
        var accepted = request(driverA);
        accepted.accept(NOW.minusSeconds(30));
        when(requests.findFirstByTripIdAndStatusOrderByRequestedAtDescIdDesc(trip.getId(), TripAssignmentStatus.PENDING))
                .thenReturn(Optional.empty());
        when(requests.findFirstByTripIdAndCandidateDriverIdAndStatusOrderByRespondedAtDescIdDesc(
                trip.getId(), driverA.getId(), TripAssignmentStatus.ACCEPTED)).thenReturn(Optional.of(accepted));

        service.unassign(trip, "Admin bỏ gán");

        assertThat(trip.getDriver()).isNull();
        ArgumentCaptor<DriverDispatchInboxEntity> saved = ArgumentCaptor.forClass(DriverDispatchInboxEntity.class);
        verify(inbox).save(saved.capture());
        assertThat(saved.getValue().getKind()).isEqualTo(DriverInboxKind.TRIP_UNASSIGNED);
        assertThat(saved.getValue().getDedupeKey()).isEqualTo(
                "direct:" + trip.getId() + ":unassigned:" + driverA.getId() + ":" + accepted.getId());
    }

    @Test
    void requestOwnershipIsScopedToAuthenticatedDriver() {
        var request = request(driverA);
        var otherDriver = principal(driverB.getId());
        when(requests.findById(request.getId())).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.accept(otherDriver, request.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(404));
        verifyNoInteractions(trips, drivers, availability, inbox);
    }

    private DriverEntity driver(String name, long id) {
        var driver = new DriverEntity(name, "0900000000", "B2-" + id);
        ReflectionTestUtils.setField(driver, "id", id);
        return driver;
    }

    private TripAssignmentRequestEntity request(DriverEntity candidate) {
        return new TripAssignmentRequestEntity(trip, candidate, admin, NOW.minusSeconds(60));
    }

    private UserAccountPrincipal principal(long driverId) {
        var principal = mock(UserAccountPrincipal.class);
        when(principal.driverId()).thenReturn(driverId);
        return principal;
    }
}
