package com.quangkhai.vehicletracking_backend.reroute.repository;

import com.quangkhai.vehicletracking_backend.reroute.entity.TripOffRouteAlertStateEntity;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TripOffRouteAlertStateRepository extends JpaRepository<TripOffRouteAlertStateEntity, Long> {
    @Query("select count(s) from TripOffRouteAlertStateEntity s where s.active = true and s.trip.status = :status")
    long countActiveForStatus(@Param("status") TripStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TripOffRouteAlertStateEntity s where s.tripId = :tripId")
    Optional<TripOffRouteAlertStateEntity> findLockedByTripId(@Param("tripId") long tripId);
}
