package com.quangkhai.vehicletracking_backend.dispatch.controller;

import com.quangkhai.vehicletracking_backend.auth.config.SecurityConfig.UserAccountPrincipal;
import com.quangkhai.vehicletracking_backend.dispatch.dto.*;
import com.quangkhai.vehicletracking_backend.dispatch.service.AdminTripDispatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/dispatch")
@RequiredArgsConstructor
public class AdminTripDispatchController {
    private final AdminTripDispatchService service;
    @GetMapping
    public DispatchDetail detail(@PathVariable long tripId) { return service.detail(tripId); }
    @PutMapping("/policy")
    public DispatchDetail policy(@AuthenticationPrincipal UserAccountPrincipal principal, @PathVariable long tripId,
                                 @Valid @RequestBody DispatchPolicyRequest input) {
        return service.policy(principal, tripId, input);
    }
    @PostMapping("/override-start")
    public DispatchOverrideResponse overrideStart(@AuthenticationPrincipal UserAccountPrincipal principal,
                                                  @PathVariable long tripId, @Valid @RequestBody DispatchOverrideRequest input) {
        return service.overrideStart(principal, tripId, input);
    }
}
