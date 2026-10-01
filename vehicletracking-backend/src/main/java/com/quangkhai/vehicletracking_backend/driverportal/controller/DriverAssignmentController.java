package com.quangkhai.vehicletracking_backend.driverportal.controller;

import com.quangkhai.vehicletracking_backend.assignment.dto.*;
import com.quangkhai.vehicletracking_backend.assignment.service.TripAssignmentService;
import com.quangkhai.vehicletracking_backend.auth.config.SecurityConfig.UserAccountPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/driver/assignment-requests")
@RequiredArgsConstructor
public class DriverAssignmentController {
    private final TripAssignmentService service;

    @GetMapping
    public List<DriverAssignmentRequestResponse> pending(@AuthenticationPrincipal UserAccountPrincipal principal) {
        return service.pendingForDriver(principal);
    }

    @PostMapping("/{requestId}/accept")
    public AssignmentActionResponse accept(@AuthenticationPrincipal UserAccountPrincipal principal,
                                            @PathVariable UUID requestId) {
        return service.accept(principal, requestId);
    }

    @PostMapping("/{requestId}/decline")
    public AssignmentActionResponse decline(@AuthenticationPrincipal UserAccountPrincipal principal,
                                             @PathVariable UUID requestId,
                                             @Valid @RequestBody DeclineAssignmentRequest input) {
        return service.decline(principal, requestId, input.reason());
    }
}
