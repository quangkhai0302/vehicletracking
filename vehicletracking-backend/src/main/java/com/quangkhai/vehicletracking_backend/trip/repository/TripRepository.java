package com.quangkhai.vehicletracking_backend.trip.repository;

import com.quangkhai.vehicletracking_backend.trip.entity.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<TripEntity, Long> {
    @EntityGraph(attributePaths = {"vehicle", "route"})
    List<TripEntity> findAllByOrderByScheduledDepartureAtDescIdDesc();
    @EntityGraph(attributePaths = {"vehicle", "route"})
    List<TripEntity> findAllByVehicleIdOrderByScheduledDepartureAtDescIdDesc(long vehicleId);
    boolean existsByVehicleIdAndStatusIn(long vehicleId, Collection<TripStatus> statuses);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TripEntity t where t.id = :id")
    Optional<TripEntity> findLockedById(@Param("id") long id);
}
