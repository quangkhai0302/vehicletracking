package com.quangkhai.vehicletracking_backend.dispatch.service;

import com.quangkhai.vehicletracking_backend.auth.config.SecurityConfig.UserAccountPrincipal;
import com.quangkhai.vehicletracking_backend.dispatch.entity.*;
import com.quangkhai.vehicletracking_backend.dispatch.repository.DriverDispatchInboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverDispatchServiceTest {
    @Mock DriverDispatchInboxRepository inbox;
    @Mock Clock operationsClock;
    @InjectMocks DriverDispatchService service;

    @Test void onlyAssignmentKindsAreQueriedBeforePagination() {
        var principal = mock(UserAccountPrincipal.class);
        when(principal.driverId()).thenReturn(9L);
        service.inbox(principal, 5);
        verify(inbox).findAssignmentInbox(9L,
                List.of(DriverInboxKind.DIRECT_ASSIGNMENT_REQUESTED, DriverInboxKind.DIRECT_ASSIGNMENT_CANCELLED,
                        DriverInboxKind.DIRECT_ASSIGNMENT_ACCEPTED), DriverInboxKind.TRIP_UNASSIGNED, PageRequest.of(0, 5));
    }

    @Test void invalidLimitDoesNotReadRepository() {
        assertThatThrownBy(() -> service.inbox(null, 51)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(inbox);
    }

    @Test void cannotReadOtherDriversInboxItem() {
        var principal = mock(UserAccountPrincipal.class);
        when(principal.driverId()).thenReturn(9L);
        when(inbox.findByIdAndRecipientDriverIdAndDismissedAtIsNull(8, 9)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.markRead(principal, 8)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test void markingOwnItemReadIsIdempotent() {
        var principal = mock(UserAccountPrincipal.class);
        when(principal.driverId()).thenReturn(9L);
        var item = new DriverDispatchInboxEntity(9, 7, null, DriverInboxKind.DIRECT_ASSIGNMENT_REQUESTED,
                "Yêu cầu nhận chuyến", null, "fixture", Instant.EPOCH);
        org.springframework.test.util.ReflectionTestUtils.setField(item, "id", 8L);
        when(inbox.findByIdAndRecipientDriverIdAndDismissedAtIsNull(8, 9)).thenReturn(Optional.of(item));
        when(operationsClock.instant()).thenReturn(Instant.EPOCH.plusSeconds(1), Instant.EPOCH.plusSeconds(2));
        service.markRead(principal, 8);
        service.markRead(principal, 8);
        assertThat(item.getReadAt()).isEqualTo(Instant.EPOCH.plusSeconds(1));
    }

    @Test void driverCanDismissOnlyTheirOwnInboxItem() {
        var principal = mock(UserAccountPrincipal.class);
        when(principal.driverId()).thenReturn(9L);
        var item = new DriverDispatchInboxEntity(9, 7, null, DriverInboxKind.DIRECT_ASSIGNMENT_REQUESTED,
                "Yêu cầu nhận chuyến", null, "fixture-dismiss", Instant.EPOCH);
        org.springframework.test.util.ReflectionTestUtils.setField(item, "id", 8L);
        when(inbox.findByIdAndRecipientDriverIdAndDismissedAtIsNull(8, 9)).thenReturn(Optional.of(item));
        when(operationsClock.instant()).thenReturn(Instant.EPOCH.plusSeconds(3));

        service.dismiss(principal, 8);

        assertThat(item.getDismissedAt()).isEqualTo(Instant.EPOCH.plusSeconds(3));
    }

    @Test void cannotDismissAnotherDriversInboxItem() {
        var principal = mock(UserAccountPrincipal.class);
        when(principal.driverId()).thenReturn(9L);
        when(inbox.findByIdAndRecipientDriverIdAndDismissedAtIsNull(8, 9)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.dismiss(principal, 8)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }
}
