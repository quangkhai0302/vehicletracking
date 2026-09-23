package com.quangkhai.vehicletracking_backend.schedule.repository;

import com.quangkhai.vehicletracking_backend.schedule.entity.TripScheduleEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TripScheduleRepository extends JpaRepository<TripScheduleEntity, Long> {
    @EntityGraph(attributePaths = {"route", "vehicle", "driver"})
    List<TripScheduleEntity> findAllByOrderByCreatedAtDescIdDesc();

    @EntityGraph(attributePaths = {"route", "vehicle", "driver"})
    List<TripScheduleEntity> findAllByDriverIdOrderByCreatedAtDescIdDesc(long driverId);

    @EntityGraph(attributePaths = {"route", "vehicle", "driver"})
    List<TripScheduleEntity> findAllByEnabledTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TripScheduleEntity s join fetch s.route join fetch s.vehicle join fetch s.driver where s.id = :id")
    Optional<TripScheduleEntity> findLockedById(@Param("id") long id);
}
