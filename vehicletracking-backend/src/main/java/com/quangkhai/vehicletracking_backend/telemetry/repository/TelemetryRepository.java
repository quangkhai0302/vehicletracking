package com.quangkhai.vehicletracking_backend.telemetry.repository;
import com.quangkhai.vehicletracking_backend.telemetry.entity.TelemetrySource;
import com.quangkhai.vehicletracking_backend.telemetry.entity.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.time.Instant;
import java.util.*;
public interface TelemetryRepository extends JpaRepository<TelemetrySampleEntity,Long>, JpaSpecificationExecutor<TelemetrySampleEntity> {
    Optional<TelemetrySampleEntity> findByEventId(UUID eventId);
    boolean existsByTripIdAndSource(long tripId, TelemetrySource source);
    long countByTripId(long tripId);

    @Query("""
            select sample from TelemetrySampleEntity sample
            where sample.tripId in :tripIds
              and sample.recordedAt < :toExclusive
            order by sample.tripId asc, sample.attemptNumber asc, sample.source asc,
                     sample.recordedAt asc, sample.id asc
            """)
    List<TelemetrySampleEntity> findAllForOperationalReport(@Param("tripIds") Collection<Long> tripIds,
                                                              @Param("toExclusive") Instant toExclusive);
}
