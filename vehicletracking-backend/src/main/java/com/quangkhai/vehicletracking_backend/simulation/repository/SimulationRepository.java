package com.quangkhai.vehicletracking_backend.simulation.repository;
import com.quangkhai.vehicletracking_backend.simulation.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SimulationRepository extends JpaRepository<SimulationRunEntity,Long> {
    Optional<SimulationRunEntity> findByTripId(long tripId);
    boolean existsByTripId(long tripId);
    List<SimulationRunEntity> findByStatusIn(Collection<SimulationStatus> statuses);
    List<SimulationRunEntity> findAllByOrderByIdAsc();
}
