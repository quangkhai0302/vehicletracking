package com.quangkhai.vehicletracking_backend.dispatch.repository;

import com.quangkhai.vehicletracking_backend.dispatch.entity.TripDispatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.List;

public interface TripDispatchRepository extends JpaRepository<TripDispatchEntity, Long> {
    @Query("select d.tripId from TripDispatchEntity d where d.nextActionAt <= :now order by d.nextActionAt, d.tripId")
    List<Long> findDueIds(Instant now, Pageable page);
    @Query("select d.tripId from TripDispatchEntity d where d.trip.schedule.id = :scheduleId order by d.tripId")
    List<Long> findTripIdsByScheduleId(long scheduleId);
}
