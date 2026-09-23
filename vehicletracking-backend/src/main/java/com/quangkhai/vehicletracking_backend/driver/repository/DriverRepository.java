package com.quangkhai.vehicletracking_backend.driver.repository;

import com.quangkhai.vehicletracking_backend.driver.entity.DriverEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DriverRepository extends JpaRepository<DriverEntity, Long> {
    List<DriverEntity> findAllByOrderByLicenseNumberAsc();
    long countByActiveTrue();
    boolean existsByLicenseNumberAndIdNot(String licenseNumber, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DriverEntity d where d.id = :id")
    Optional<DriverEntity> findLockedById(@Param("id") long id);
}
