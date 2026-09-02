package com.quangkhai.vehiceltracking_backend.repository;

import com.quangkhai.vehiceltracking_backend.entity.TripCheckIn;
import com.quangkhai.vehiceltracking_backend.enums.CheckInStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TripCheckInRepository extends JpaRepository<TripCheckIn, Long> {
    List<TripCheckIn> findByTripIdOrderByStopOrderAsc(Long tripId);
    Optional<TripCheckIn> findByTripIdAndStationId(Long tripId, Long stationId);
    Optional<TripCheckIn> findFirstByTripIdAndStatusOrderByStopOrderAsc(Long tripId, CheckInStatus status);
}
