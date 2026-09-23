package com.quangkhai.vehicletracking_backend.vehicle.repository;

import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<VehicleEntity, Long> {
    @EntityGraph(attributePaths = "driver")
    List<VehicleEntity> findAllByOrderByPlateNumberAsc();
    long countByActiveTrue();
    boolean existsByPlateNumberAndIdNot(String plateNumber, Long id);
    boolean existsByDriverIdAndActiveTrue(long driverId);
    boolean existsByDriverIdAndActiveTrueAndIdNot(long driverId, long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VehicleEntity v left join fetch v.driver where v.id = :id")
    Optional<VehicleEntity> findLockedById(@Param("id") long id);
}
