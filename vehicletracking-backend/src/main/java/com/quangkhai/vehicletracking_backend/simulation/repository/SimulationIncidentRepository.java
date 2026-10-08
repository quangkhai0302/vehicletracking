package com.quangkhai.vehicletracking_backend.simulation.repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.quangkhai.vehicletracking_backend.simulation.entity.SimulationIncidentEntity;
import com.quangkhai.vehicletracking_backend.simulation.entity.SimulationIncidentStatus;

import jakarta.persistence.LockModeType;

public interface SimulationIncidentRepository extends JpaRepository<SimulationIncidentEntity, Long> {
    Optional<SimulationIncidentEntity> findByIdempotencyKey(UUID idempotencyKey);

    boolean existsByTripIdAndAttemptNumberAndStatusIn(long tripId, int attemptNumber,
                                                       Collection<SimulationIncidentStatus> statuses);

    java.util.List<SimulationIncidentEntity> findByTripIdAndAttemptNumberAndStatusInOrderByCreatedAtDesc(
            long tripId, int attemptNumber, Collection<SimulationIncidentStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from SimulationIncidentEntity i join fetch i.trip where i.id = :id")
    Optional<SimulationIncidentEntity> findLockedById(@Param("id") long id);
}
