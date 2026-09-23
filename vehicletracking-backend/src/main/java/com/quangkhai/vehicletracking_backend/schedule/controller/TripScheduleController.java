package com.quangkhai.vehicletracking_backend.schedule.controller;

import com.quangkhai.vehicletracking_backend.schedule.dto.ScheduleResponse;
import com.quangkhai.vehicletracking_backend.schedule.dto.ScheduleUpsertRequest;
import com.quangkhai.vehicletracking_backend.schedule.service.TripScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
public class TripScheduleController {
    private final TripScheduleService service;
    @GetMapping public List<ScheduleResponse> findAll() { return service.findAll(); }
    @GetMapping("/{id}") public ScheduleResponse findById(@PathVariable long id) { return service.findById(id); }
    @PostMapping public ResponseEntity<ScheduleResponse> create(@Valid @RequestBody ScheduleUpsertRequest input) {
        ScheduleResponse created = service.create(input);
        return ResponseEntity.created(URI.create("/api/v1/schedules/" + created.id())).body(created);
    }
    @PutMapping("/{id}") public ScheduleResponse update(@PathVariable long id, @Valid @RequestBody ScheduleUpsertRequest input) { return service.update(id, input); }
    @PostMapping("/{id}/enable") public ScheduleResponse enable(@PathVariable long id) { return service.enable(id); }
    @PostMapping("/{id}/disable") public ScheduleResponse disable(@PathVariable long id) { return service.disable(id); }
}
