package com.quangkhai.vehicletracking_backend.reroute;

import com.quangkhai.vehicletracking_backend.reroute.entity.TripNotificationEntity;
import com.quangkhai.vehicletracking_backend.reroute.dto.NotificationResponse;
import com.quangkhai.vehicletracking_backend.reroute.dto.RouteRevisionResponse;
import com.quangkhai.vehicletracking_backend.reroute.entity.TripRouteRevisionEntity;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripNotificationRepository;
import com.quangkhai.vehicletracking_backend.reroute.service.NotificationService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class NotificationServiceTest {
    @Test
    void legacyRerouteMessagesAreNeutralInResponsesWithoutChangingStoredHistory() {
        TripNotificationEntity notification = mock(TripNotificationEntity.class, RETURNS_DEEP_STUBS);
        when(notification.getReason()).thenReturn("HERE Routing không trả về tuyến khả dụng hoặc tuyến mới không cải thiện ETA.");
        when(notification.getRevision()).thenReturn(null);

        assertThat(NotificationResponse.from(notification).reason()).isEqualTo(RerouteMessages.UNAVAILABLE);
        assertThat(notification.getReason()).contains("HERE Routing");

        when(notification.getReason()).thenReturn("Phát hiện đường bị đóng/chặn từ HERE Traffic");
        assertThat(NotificationResponse.from(notification).reason()).isEqualTo(RerouteMessages.ROAD_CLOSED);

        TripRouteRevisionEntity revision = mock(TripRouteRevisionEntity.class, RETURNS_DEEP_STUBS);
        when(revision.getReasonDetail()).thenReturn("Phát hiện đường bị đóng/chặn từ HERE Traffic");
        assertThat(RouteRevisionResponse.from(revision).reasonDetail()).isEqualTo(RerouteMessages.ROAD_CLOSED);
        assertThat(revision.getReasonDetail()).contains("HERE Traffic");
    }

    @Test
    void otherNotificationDetailsAndMissingRevisionReasonsArePreserved() {
        TripNotificationEntity notification = mock(TripNotificationEntity.class, RETURNS_DEEP_STUBS);
        when(notification.getRevision()).thenReturn(null);
        when(notification.getReason()).thenReturn("Tài xế báo không thể thực hiện chuyến.");
        assertThat(NotificationResponse.from(notification).reason()).isEqualTo(notification.getReason());

        TripRouteRevisionEntity revision = mock(TripRouteRevisionEntity.class, RETURNS_DEEP_STUBS);
        when(revision.getReasonDetail()).thenReturn(null);
        assertThat(RouteRevisionResponse.from(revision).reasonDetail()).isNull();
    }

    @Test
    void deleteDismissesNotificationInsteadOfDeletingItsReportHistory() {
        TripNotificationRepository notifications = mock(TripNotificationRepository.class);
        Instant now = Instant.parse("2026-09-21T08:00:00Z");
        NotificationService service = new NotificationService(notifications, Clock.fixed(now, ZoneOffset.UTC));
        TripNotificationEntity notification = mock(TripNotificationEntity.class, CALLS_REAL_METHODS);
        when(notifications.findById(7L)).thenReturn(Optional.of(notification));

        service.delete(7L);

        verify(notification).dismiss(now);
        verify(notifications, never()).delete(any());
    }
}
