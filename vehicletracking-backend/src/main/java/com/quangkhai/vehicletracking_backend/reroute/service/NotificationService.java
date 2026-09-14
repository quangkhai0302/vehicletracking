package com.quangkhai.vehicletracking_backend.reroute.service;

import com.quangkhai.vehicletracking_backend.reroute.dto.NotificationResponse;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final TripNotificationRepository notifications;
    private final Clock operationsClock;

    @Transactional(readOnly = true)
    public List<NotificationResponse> recent(boolean unreadOnly) {
        var rows = unreadOnly ? notifications.findTop50ByReadAtIsNullOrderByCreatedAtDescIdDesc()
                : notifications.findTop50ByOrderByCreatedAtDescIdDesc();
        return rows.stream().map(NotificationResponse::from).toList();
    }

    @Transactional
    public NotificationResponse markRead(long id) {
        var item = notifications.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thông báo."));
        if (item.getReadAt() == null) item.markRead(operationsClock.instant());
        return NotificationResponse.from(item);
    }

    @Transactional
    public int markAllRead() {
        var now = operationsClock.instant();
        var rows = notifications.findAllByReadAtIsNullOrderByCreatedAtDescIdDesc();
        rows.forEach(item -> item.markRead(now));
        return rows.size();
    }

    @Transactional
    public void delete(long id) {
        var item = notifications.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thông báo."));
        notifications.delete(item);
    }
}
