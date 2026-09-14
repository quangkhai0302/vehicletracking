package com.quangkhai.vehicletracking_backend.telemetry.repository;
import com.quangkhai.vehicletracking_backend.telemetry.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.*;
public interface TelemetryRepository extends JpaRepository<TelemetrySampleEntity,Long>, JpaSpecificationExecutor<TelemetrySampleEntity> {
    Optional<TelemetrySampleEntity> findByEventId(UUID eventId);
    boolean existsByTripIdAndSource(long tripId, TelemetrySource source);
    long countByTripId(long tripId);
}
