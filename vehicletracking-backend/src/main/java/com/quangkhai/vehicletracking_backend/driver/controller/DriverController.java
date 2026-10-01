package com.quangkhai.vehicletracking_backend.driver.controller;

import com.quangkhai.vehicletracking_backend.driver.dto.DriverResponse;
import com.quangkhai.vehicletracking_backend.driver.dto.DriverUpsertRequest;
import com.quangkhai.vehicletracking_backend.driver.service.DriverService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/drivers")
@RequiredArgsConstructor
public class DriverController {
    private final DriverService service;

    @GetMapping
    public List<DriverResponse> findAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public DriverResponse findById(@PathVariable long id) { return service.findById(id); }

    @PostMapping
    public ResponseEntity<DriverResponse> create(@Valid @RequestBody DriverUpsertRequest request) {
        DriverResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/drivers/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public DriverResponse update(@PathVariable long id, @Valid @RequestBody DriverUpsertRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadAvatar(@PathVariable long id, @RequestPart("file") MultipartFile file) {
        service.updateAvatar(id, file);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/avatar")
    public ResponseEntity<Void> deleteAvatar(@PathVariable long id) {
        service.clearAvatar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/avatar")
    public ResponseEntity<byte[]> avatar(@PathVariable long id) {
        DriverService.AvatarFile avatar = service.avatar(id);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .body(avatar.data());
    }
}
