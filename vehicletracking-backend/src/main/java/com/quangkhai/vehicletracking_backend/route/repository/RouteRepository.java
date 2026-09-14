package com.quangkhai.vehicletracking_backend.route.repository;

import com.quangkhai.vehicletracking_backend.route.entity.RouteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

@Repository
public interface RouteRepository extends JpaRepository<RouteEntity, Long> {

    List<RouteEntity> findAllByOrderByCreatedAtDescIdDesc();
    List<RouteEntity> findAllByActiveTrueOrderByCreatedAtDescIdDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RouteEntity r where r.id = :id")
    java.util.Optional<RouteEntity> findLockedById(@Param("id") long id);
}
