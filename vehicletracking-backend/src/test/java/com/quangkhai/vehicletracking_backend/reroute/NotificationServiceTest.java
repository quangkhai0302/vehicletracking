package com.quangkhai.vehicletracking_backend.reroute;

import com.quangkhai.vehicletracking_backend.reroute.entity.TripNotificationEntity;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripNotificationRepository;
import com.quangkhai.vehicletracking_backend.reroute.service.NotificationService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.Mockito.*;

class NotificationServiceTest {
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
