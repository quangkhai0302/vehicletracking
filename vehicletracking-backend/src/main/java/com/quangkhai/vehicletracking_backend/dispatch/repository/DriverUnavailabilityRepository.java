package com.quangkhai.vehicletracking_backend.dispatch.repository;

import com.quangkhai.vehicletracking_backend.dispatch.entity.DriverUnavailabilityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;

public interface DriverUnavailabilityRepository extends JpaRepository<DriverUnavailabilityEntity, Long> {
    boolean existsByDriverIdAndStartsAtLessThanAndEndsAtGreaterThan(long driverId, Instant end, Instant start);
}
