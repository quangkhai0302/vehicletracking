package com.quangkhai.vehicletracking_backend.driverportal.controller;

import com.quangkhai.vehicletracking_backend.auth.config.SecurityConfig.UserAccountPrincipal;
import com.quangkhai.vehicletracking_backend.dispatch.dto.*;
import com.quangkhai.vehicletracking_backend.dispatch.service.DriverDispatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/driver")
@RequiredArgsConstructor
public class DriverDispatchController {
    private final DriverDispatchService service;

    @GetMapping("/dispatch/inbox")
    public List<DriverInboxResponse> inbox(@AuthenticationPrincipal UserAccountPrincipal principal,
                                           @RequestParam(defaultValue = "50") int limit) {
        return service.inbox(principal, limit);
    }
    @PostMapping("/dispatch/inbox/{id}/read")
    public DriverInboxResponse markRead(@AuthenticationPrincipal UserAccountPrincipal principal, @PathVariable long id) {
        return service.markRead(principal, id);
    }
}
