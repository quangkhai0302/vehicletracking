package com.quangkhai.vehicletracking_backend.checkin.controller;

import com.quangkhai.vehicletracking_backend.checkin.dto.TripCheckInsResponse;
import com.quangkhai.vehicletracking_backend.checkin.service.CheckInQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/trips/{tripId}/check-ins") @RequiredArgsConstructor
public class CheckInController {
    private final CheckInQueryService query;
    @GetMapping public TripCheckInsResponse find(@PathVariable long tripId, @RequestParam(required=false) Integer attemptNumber) {
        return attemptNumber==null ? query.find(tripId) : query.findAttempt(tripId,attemptNumber);
    }
}
