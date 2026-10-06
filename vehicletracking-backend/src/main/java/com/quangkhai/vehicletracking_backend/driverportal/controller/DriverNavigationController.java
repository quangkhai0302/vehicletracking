package com.quangkhai.vehicletracking_backend.driverportal.controller;

import com.quangkhai.vehicletracking_backend.auth.config.SecurityConfig.UserAccountPrincipal;
import com.quangkhai.vehicletracking_backend.driverportal.dto.*;
import com.quangkhai.vehicletracking_backend.driverportal.service.DriverNavigationService;
import com.quangkhai.vehicletracking_backend.simulation.dto.SimulationIncidentCreateRequest;
import com.quangkhai.vehicletracking_backend.simulation.dto.SimulationIncidentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/driver/trips/{tripId}")
@RequiredArgsConstructor
public class DriverNavigationController {
    private final DriverNavigationService service;

    @GetMapping("/navigation")
    public DriverNavigationResponse navigation(@AuthenticationPrincipal UserAccountPrincipal principal, @PathVariable long tripId) {
        return service.navigation(principal, tripId);
    }
    @PostMapping("/start")
    public DriverNavigationResponse start(@AuthenticationPrincipal UserAccountPrincipal principal, @PathVariable long tripId) {
        return service.start(principal, tripId);
    }
    @PostMapping("/route-options")
    public DriverRouteOptionsResponse options(@AuthenticationPrincipal UserAccountPrincipal principal, @PathVariable long tripId) {
        return service.options(principal, tripId);
    }
    @PostMapping("/route-options/{token}/apply")
    public DriverNavigationResponse apply(@AuthenticationPrincipal UserAccountPrincipal principal, @PathVariable long tripId,
                                          @PathVariable UUID token, @Valid @RequestBody DriverRouteApplyRequest request) {
        return service.apply(principal, tripId, token, request.optionIndex());
    }

    @PostMapping("/simulation/incidents")
    @ResponseStatus(HttpStatus.CREATED)
    public SimulationIncidentResponse reportIncident(@AuthenticationPrincipal UserAccountPrincipal principal,
                                                       @PathVariable long tripId,
                                                       @Valid @RequestBody SimulationIncidentCreateRequest request) {
        return service.reportIncident(principal, tripId, request);
    }
}
