package com.quangkhai.vehiceltracking_backend.repository;

import com.quangkhai.vehiceltracking_backend.entity.Trip;
import com.quangkhai.vehiceltracking_backend.enums.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {
    Optional<Trip> findByTripCode(String tripCode);
    List<Trip> findByStatus(TripStatus status);
    List<Trip> findByVehicleIdAndStatus(Long vehicleId, TripStatus status);
    boolean existsByRouteId(Long routeId);
}
