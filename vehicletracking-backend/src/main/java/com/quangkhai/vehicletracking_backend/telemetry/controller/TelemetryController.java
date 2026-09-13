package com.quangkhai.vehicletracking_backend.telemetry.controller;
import com.quangkhai.vehicletracking_backend.telemetry.dto.*;
import com.quangkhai.vehicletracking_backend.telemetry.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
@RestController @RequestMapping("/api/v1/telemetry") @RequiredArgsConstructor
public class TelemetryController {
    private final TelemetryService telemetry;
    private final OperationsSnapshotService snapshots;
    private final OperationsStreamService stream;
    @PostMapping public TelemetryResponse ingest(@Valid @RequestBody TelemetryRequest body) { return telemetry.ingestGps(body); }
    @GetMapping("/snapshot") public OperationsSnapshot snapshot() { return snapshots.snapshot(); }
    @GetMapping(value="/stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Accel-Buffering","no").body(stream.subscribe());
    }
}
