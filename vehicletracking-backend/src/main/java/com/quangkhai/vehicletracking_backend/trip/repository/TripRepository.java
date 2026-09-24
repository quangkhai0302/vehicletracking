package com.quangkhai.vehicletracking_backend.trip.repository;

import com.quangkhai.vehicletracking_backend.trip.entity.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<TripEntity, Long> {
    @EntityGraph(attributePaths = {"route", "stops", "schedule"})
    @Query("""
            select distinct t from TripEntity t
            where t.scheduledDepartureAt >= :from
              and t.scheduledDepartureAt < :toExclusive
              and (:vehicleId is null or t.vehicle.id = :vehicleId)
              and (:driverId is null or t.driver.id = :driverId)
            order by t.scheduledDepartureAt asc, t.id asc
            """)
    List<TripEntity> findAllForOperationalReport(@Param("from") Instant from,
                                                   @Param("toExclusive") Instant toExclusive,
                                                   @Param("vehicleId") Long vehicleId,
                                                   @Param("driverId") Long driverId);

    @EntityGraph(attributePaths = {"route", "stops", "schedule"})
    List<TripEntity> findAllByStatus(TripStatus status);
    long countByStatus(TripStatus status);
    @EntityGraph(attributePaths = {"vehicle", "route", "driver", "schedule"})
    List<TripEntity> findAllByOrderByScheduledDepartureAtDescIdDesc();
    @EntityGraph(attributePaths = {"vehicle", "route", "driver", "schedule"})
    List<TripEntity> findAllByVehicleIdOrderByScheduledDepartureAtDescIdDesc(long vehicleId);
    @EntityGraph(attributePaths = {"vehicle", "route", "driver", "stops", "schedule"})
    List<TripEntity> findAllByDriverIdOrderByScheduledDepartureAtDescIdDesc(long driverId);
    @EntityGraph(attributePaths = {"vehicle", "route", "driver", "stops", "schedule"})
    Optional<TripEntity> findByIdAndDriverId(long id, long driverId);
    boolean existsByVehicleIdAndStatusIn(long vehicleId, Collection<TripStatus> statuses);
    boolean existsByVehicleIdAndScheduledDepartureAtAndStatusIn(long vehicleId, Instant scheduledDepartureAt,
            Collection<TripStatus> statuses);
    boolean existsByDriverIdAndStatusIn(long driverId, Collection<TripStatus> statuses);
    boolean existsByDriverIdAndScheduledDepartureAtAndStatusIn(long driverId, Instant scheduledDepartureAt,
            Collection<TripStatus> statuses);
    boolean existsByDriverIdAndStatusAndIdNot(long driverId, TripStatus status, long id);
    @EntityGraph(attributePaths = {"route"})
    List<TripEntity> findAllByVehicleIdAndStatusIn(long vehicleId, Collection<TripStatus> statuses);
    boolean existsByRouteId(long routeId);
    boolean existsByRouteIdAndStatusIn(long routeId, Collection<TripStatus> statuses);
    boolean existsByScheduleIdAndScheduleOccurrenceAt(Long scheduleId, Instant scheduleOccurrenceAt);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TripEntity t where t.id = :id")
    Optional<TripEntity> findLockedById(@Param("id") long id);
}
