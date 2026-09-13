package com.quangkhai.vehicletracking_backend.telemetry.repository;
import com.quangkhai.vehicletracking_backend.telemetry.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface TelemetryRepository extends JpaRepository<TelemetrySampleEntity,Long> {
    Optional<TelemetrySampleEntity> findByEventId(UUID eventId);
    boolean existsByTripIdAndSource(long tripId, TelemetrySource source);
    long countByTripId(long tripId);
}
