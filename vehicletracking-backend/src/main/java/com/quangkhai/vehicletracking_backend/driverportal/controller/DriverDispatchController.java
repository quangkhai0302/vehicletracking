package com.quangkhai.vehicletracking_backend.driverportal.controller;

import com.quangkhai.vehicletracking_backend.auth.config.SecurityConfig.UserAccountPrincipal;
import com.quangkhai.vehicletracking_backend.dispatch.dto.*;
import com.quangkhai.vehicletracking_backend.dispatch.service.DriverDispatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/driver")
@RequiredArgsConstructor
public class DriverDispatchController {
    private final DriverDispatchService service;

    @GetMapping("/trips/{tripId}/dispatch")
    public DriverDispatchDetail detail(@AuthenticationPrincipal UserAccountPrincipal principal, @PathVariable long tripId) {
        return service.detail(principal, tripId);
    }
    @PostMapping("/trips/{tripId}/dispatch/ready")
    public DriverDispatchDetail ready(@AuthenticationPrincipal UserAccountPrincipal principal, @PathVariable long tripId,
                                      @Valid @RequestBody ExpectedDispatchRevision input) {
        return service.ready(principal, tripId, input.expectedRevision());
    }
    @PostMapping("/trips/{tripId}/dispatch/unavailable")
    public DriverUnavailableResponse unavailable(@AuthenticationPrincipal UserAccountPrincipal principal,
                                                 @PathVariable long tripId, @Valid @RequestBody DriverUnavailableRequest input) {
        return service.unavailable(principal, tripId, input.expectedRevision(), input.reason());
    }
    @GetMapping("/dispatch/offers")
    public List<DriverOfferResponse> offers(@AuthenticationPrincipal UserAccountPrincipal principal) {
        return service.offers(principal);
    }
    @PostMapping("/dispatch/offers/{offerId}/accept")
    public DriverOfferActionResponse accept(@AuthenticationPrincipal UserAccountPrincipal principal, @PathVariable UUID offerId,
                                            @Valid @RequestBody ExpectedDispatchRevision input) {
        return service.accept(principal, offerId, input.expectedRevision());
    }
    @PostMapping("/dispatch/offers/{offerId}/decline")
    public DriverOfferActionResponse decline(@AuthenticationPrincipal UserAccountPrincipal principal, @PathVariable UUID offerId,
                                             @Valid @RequestBody DeclineDispatchOfferRequest input) {
        return service.decline(principal, offerId, input.expectedRevision(), input.reason());
    }
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
