package com.quangkhai.vehicletracking_backend.checkin.repository;

import com.quangkhai.vehicletracking_backend.checkin.entity.TripStopVisitEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Collection;

public interface TripStopVisitRepository extends JpaRepository<TripStopVisitEntity, Long> {
    @Query("select v from TripStopVisitEntity v where v.trip.id = :tripId and v.attemptNumber = v.trip.attemptNumber order by v.stopSequence")
    List<TripStopVisitEntity> findAllByTripIdOrderByStopSequenceAsc(long tripId);
    @Query("select v from TripStopVisitEntity v where v.trip.id in :tripIds and v.attemptNumber = v.trip.attemptNumber order by v.trip.id, v.stopSequence")
    List<TripStopVisitEntity> findAllByTripIdInOrderByTripIdAscStopSequenceAsc(Collection<Long> tripIds);
    @Query("select (count(v) > 0) from TripStopVisitEntity v where v.trip.id = :tripId and v.stopSequence = :stopSequence and v.attemptNumber = v.trip.attemptNumber")
    boolean existsByTripIdAndStopSequence(long tripId, int stopSequence);
    List<TripStopVisitEntity> findAllByTripIdAndAttemptNumberOrderByStopSequenceAsc(long tripId, int attemptNumber);
}
