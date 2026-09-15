package com.quangkhai.vehicletracking_backend.simulation.repository;
import com.quangkhai.vehicletracking_backend.simulation.entity.SimulationAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface SimulationAttemptRepository extends JpaRepository<SimulationAttemptEntity, Long> {
    List<SimulationAttemptEntity> findAllByTripIdOrderByAttemptNumberDesc(long tripId);
}
