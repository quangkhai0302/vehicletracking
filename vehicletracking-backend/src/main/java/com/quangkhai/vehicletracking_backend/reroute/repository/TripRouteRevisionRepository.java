package com.quangkhai.vehicletracking_backend.reroute.repository;

import com.quangkhai.vehicletracking_backend.reroute.entity.TripRouteRevisionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TripRouteRevisionRepository extends JpaRepository<TripRouteRevisionEntity, Long> {
    List<TripRouteRevisionEntity> findAllByTripIdOrderByRevisionNumberDesc(long tripId);
    Optional<TripRouteRevisionEntity> findTopByTripIdAndStatusOrderByRevisionNumberDesc(long tripId, com.quangkhai.vehicletracking_backend.reroute.entity.RouteRevisionStatus status);
    int countByTripId(long tripId);
}
