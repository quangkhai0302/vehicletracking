package com.quangkhai.vehicletracking_backend.telemetry.controller;
import com.quangkhai.vehicletracking_backend.telemetry.dto.*;
import com.quangkhai.vehicletracking_backend.telemetry.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.format.annotation.DateTimeFormat;
import com.quangkhai.vehicletracking_backend.telemetry.entity.TelemetrySource;
@RestController @RequestMapping("/api/v1/telemetry") @RequiredArgsConstructor
public class TelemetryController {
    private final TelemetryService telemetry;
    private final OperationsSnapshotService snapshots;
    private final OperationsStreamService stream;
    private final TelemetryHistoryService history;
    @PostMapping public TelemetryResponse ingest(@Valid @RequestBody TelemetryRequest body) { return telemetry.ingestGps(body); }
    @GetMapping("/history")
    public TelemetryPageResponse history(@RequestParam(required = false) Long tripId,
                                         @RequestParam(required = false) Long vehicleId,
                                         @RequestParam(required = false) TelemetrySource source,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) java.time.Instant from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) java.time.Instant to,
                                         @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size) {
        return history.find(tripId, vehicleId, source, from, to, page, size);
    }
    @GetMapping("/snapshot") public OperationsSnapshot snapshot() { return snapshots.snapshot(); }
    @GetMapping(value="/stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Accel-Buffering","no").body(stream.subscribe());
    }
}
