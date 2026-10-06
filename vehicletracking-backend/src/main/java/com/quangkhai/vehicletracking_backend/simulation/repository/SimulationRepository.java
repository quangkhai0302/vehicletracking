package com.quangkhai.vehicletracking_backend.simulation.repository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.quangkhai.vehicletracking_backend.simulation.entity.SimulationRunEntity;
import com.quangkhai.vehicletracking_backend.simulation.entity.SimulationStatus;
public interface SimulationRepository extends JpaRepository<SimulationRunEntity,Long> {
    Optional<SimulationRunEntity> findByTripId(long tripId);
    boolean existsByTripId(long tripId);
    List<SimulationRunEntity> findByStatusIn(Collection<SimulationStatus> statuses);
    List<SimulationRunEntity> findAllByOrderByIdAsc();
    @org.springframework.data.jpa.repository.Query("""
        select r from SimulationRunEntity r join TripEntity t on t.id=r.tripId
        where coalesce(r.metadata.attemptStartedAt,t.startedAt,r.createdAt)>=:from
          and not (t.status=com.quangkhai.vehicletracking_backend.trip.entity.TripStatus.SCHEDULED
            and r.status=com.quangkhai.vehicletracking_backend.simulation.entity.SimulationStatus.PAUSED
            and r.elapsedSeconds=0 and t.startedAt is null and r.metadata.attemptStartedAt is null)
          and coalesce(r.metadata.attemptStartedAt,t.startedAt,r.createdAt)<:to
          and (:vehicleId is null or r.metadata.vehicleId=:vehicleId)
          and (:driverId is null or r.metadata.driverId=:driverId)
        """)
    List<SimulationRunEntity> findForSimulationReport(java.time.Instant from,java.time.Instant to,Long vehicleId,Long driverId);
}
