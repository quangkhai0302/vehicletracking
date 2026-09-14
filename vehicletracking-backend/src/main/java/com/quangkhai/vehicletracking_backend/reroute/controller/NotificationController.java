package com.quangkhai.vehicletracking_backend.reroute.controller;

import com.quangkhai.vehicletracking_backend.reroute.dto.NotificationResponse;
import com.quangkhai.vehicletracking_backend.reroute.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notifications;
    @GetMapping public List<NotificationResponse> recent(@RequestParam(defaultValue = "false") boolean unreadOnly) { return notifications.recent(unreadOnly); }
    @PostMapping("/read-all") public java.util.Map<String, Integer> readAll() { return java.util.Map.of("updated", notifications.markAllRead()); }
    @PostMapping("/{id}/read") public NotificationResponse read(@PathVariable long id) { return notifications.markRead(id); }
    @DeleteMapping("/{id}") public org.springframework.http.ResponseEntity<Void> delete(@PathVariable long id) {
        notifications.delete(id); return org.springframework.http.ResponseEntity.noContent().build();
    }
}
