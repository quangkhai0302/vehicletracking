package com.quangkhai.vehicletracking_backend.telemetry.service;

import com.quangkhai.vehicletracking_backend.telemetry.dto.*;
import com.quangkhai.vehicletracking_backend.telemetry.entity.*;
import com.quangkhai.vehicletracking_backend.telemetry.repository.TelemetryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TelemetryHistoryService {
    private final TelemetryRepository samples;

    @Transactional(readOnly = true)
    public TelemetryPageResponse find(Long tripId, Long vehicleId, TelemetrySource source,
                                      Instant from, Instant to, int page, int size) {
        if (page < 0 || size < 1 || size > 500) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page phải >= 0 và size trong khoảng 1–500.");
        if (from != null && to != null && from.isAfter(to)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from không được sau to.");
        Specification<TelemetrySampleEntity> spec = (root, query, cb) -> cb.conjunction();
        if (tripId != null) { if (tripId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tripId phải là số dương."); spec = spec.and((root, q, cb) -> cb.equal(root.get("tripId"), tripId)); }
        if (vehicleId != null) { if (vehicleId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "vehicleId phải là số dương."); spec = spec.and((root, q, cb) -> cb.equal(root.get("vehicleId"), vehicleId)); }
        if (source != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("source"), source));
        if (from != null) spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("recordedAt"), from));
        if (to != null) spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("recordedAt"), to));
        var result = samples.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "recordedAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        return new TelemetryPageResponse(result.getContent().stream().map(TelemetryResponse::from).toList(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
}
