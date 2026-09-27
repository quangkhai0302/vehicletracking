package com.quangkhai.vehicletracking_backend.auth.controller;

import com.quangkhai.vehicletracking_backend.auth.dto.DriverAccountCreateRequest;
import com.quangkhai.vehicletracking_backend.auth.dto.DriverPasswordResetRequest;
import com.quangkhai.vehicletracking_backend.auth.dto.UserAccountResponse;
import com.quangkhai.vehicletracking_backend.auth.service.UserAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserAccountController {
    private final UserAccountService service;

    @GetMapping
    public List<UserAccountResponse> findAll() { return service.findAll(); }

    @PostMapping("/driver")
    public ResponseEntity<UserAccountResponse> createDriver(@Valid @RequestBody DriverAccountCreateRequest request) {
        UserAccountResponse created = service.createDriverAccount(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
    }

    @PostMapping("/{id}/enable")
    public UserAccountResponse enable(@PathVariable long id) { return service.setActive(id, true); }

    @PostMapping("/{id}/disable")
    public UserAccountResponse disable(@PathVariable long id) { return service.setActive(id, false); }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Void> resetDriverPassword(
            @PathVariable long id, @Valid @RequestBody DriverPasswordResetRequest request) {
        service.resetDriverPassword(id, request);
        return ResponseEntity.noContent().build();
    }
}
