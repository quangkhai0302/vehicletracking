package com.quangkhai.vehicletracking_backend.checkin.repository;

import com.quangkhai.vehicletracking_backend.checkin.entity.TripCheckInStateEntity;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface TripCheckInStateRepository extends JpaRepository<TripCheckInStateEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TripCheckInStateEntity s where s.tripId = :tripId")
    Optional<TripCheckInStateEntity> findLockedByTripId(long tripId);
    List<TripCheckInStateEntity> findAllByTripIdIn(Collection<Long> tripIds);
}
