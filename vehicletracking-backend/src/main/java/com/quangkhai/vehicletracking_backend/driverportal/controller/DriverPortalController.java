package com.quangkhai.vehicletracking_backend.driverportal.controller;

import com.quangkhai.vehicletracking_backend.auth.config.SecurityConfig.UserAccountPrincipal;
import com.quangkhai.vehicletracking_backend.driverportal.service.DriverPortalService;
import com.quangkhai.vehicletracking_backend.schedule.dto.ScheduleResponse;
import com.quangkhai.vehicletracking_backend.trip.dto.TripDetailResponse;
import com.quangkhai.vehicletracking_backend.trip.dto.TripSummaryResponse;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/driver")
@RequiredArgsConstructor
public class DriverPortalController {
    private final DriverPortalService service;

    @GetMapping("/trips")
    public List<TripSummaryResponse> trips(
            @AuthenticationPrincipal UserAccountPrincipal principal,
            @RequestParam(required = false) TripStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.trips(principal, status, from, to);
    }

    @GetMapping("/trips/{id}")
    public TripDetailResponse trip(@AuthenticationPrincipal UserAccountPrincipal principal, @PathVariable long id) {
        return service.trip(principal, id);
    }

    @GetMapping("/schedules")
    public List<ScheduleResponse> schedules(@AuthenticationPrincipal UserAccountPrincipal principal) {
        return service.schedules(principal);
    }
}
