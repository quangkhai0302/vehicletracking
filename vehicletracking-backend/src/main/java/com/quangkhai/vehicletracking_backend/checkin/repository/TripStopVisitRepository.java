package com.quangkhai.vehicletracking_backend.checkin.repository;

import com.quangkhai.vehicletracking_backend.checkin.entity.TripStopVisitEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Collection;

public interface TripStopVisitRepository extends JpaRepository<TripStopVisitEntity, Long> {
    List<TripStopVisitEntity> findAllByTripIdOrderByStopSequenceAsc(long tripId);
    List<TripStopVisitEntity> findAllByTripIdInOrderByTripIdAscStopSequenceAsc(Collection<Long> tripIds);
    boolean existsByTripIdAndStopSequence(long tripId, int stopSequence);
}
