package com.quangkhai.vehicletracking_backend.simulation.repository;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.quangkhai.vehicletracking_backend.simulation.entity.SimulationAttemptEntity;
public interface SimulationAttemptRepository extends JpaRepository<SimulationAttemptEntity, Long> {
    List<SimulationAttemptEntity> findAllByTripIdOrderByAttemptNumberDesc(long tripId);
    @org.springframework.data.jpa.repository.Query("""
        select a from SimulationAttemptEntity a
        where coalesce(a.metadata.attemptStartedAt,a.startedAt,a.archivedAt)>=:from
          and coalesce(a.metadata.attemptStartedAt,a.startedAt,a.archivedAt)<:to
          and (:vehicleId is null or a.metadata.vehicleId=:vehicleId)
          and (:driverId is null or a.metadata.driverId=:driverId)
        """)
    List<SimulationAttemptEntity> findForSimulationReport(java.time.Instant from,java.time.Instant to,Long vehicleId,Long driverId);
}
