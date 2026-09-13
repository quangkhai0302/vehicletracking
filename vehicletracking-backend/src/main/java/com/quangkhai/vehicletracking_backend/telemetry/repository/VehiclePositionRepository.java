package com.quangkhai.vehicletracking_backend.telemetry.repository;
import com.quangkhai.vehicletracking_backend.telemetry.entity.VehiclePositionEntity;
import org.springframework.data.jpa.repository.*;
import java.util.List;
public interface VehiclePositionRepository extends JpaRepository<VehiclePositionEntity,Long> {
    @EntityGraph(attributePaths="sample") List<VehiclePositionEntity> findAllByOrderByVehicleIdAsc();
}
