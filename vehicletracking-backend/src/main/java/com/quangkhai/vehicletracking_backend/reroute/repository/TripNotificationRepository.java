package com.quangkhai.vehicletracking_backend.reroute.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.quangkhai.vehicletracking_backend.reroute.entity.NotificationType;
import com.quangkhai.vehicletracking_backend.reroute.entity.TripNotificationEntity;

public interface TripNotificationRepository extends JpaRepository<TripNotificationEntity, Long> {
    List<TripNotificationEntity> findTop50ByDismissedAtIsNullOrderByCreatedAtDescIdDesc();
    List<TripNotificationEntity> findTop50ByReadAtIsNullAndDismissedAtIsNullOrderByCreatedAtDescIdDesc();
    long countByReadAtIsNullAndDismissedAtIsNull();
    List<TripNotificationEntity> findAllByReadAtIsNullAndDismissedAtIsNullOrderByCreatedAtDescIdDesc();
    List<TripNotificationEntity> findAllByTripIdOrderByCreatedAtDescIdDesc(long tripId);
    Optional<TripNotificationEntity> findByDedupeKey(String dedupeKey);
    @Query("""
        select n from TripNotificationEntity n join fetch n.trip
        where n.trip.id in :tripIds and n.attemptNumber is not null
          and n.source=com.quangkhai.vehicletracking_backend.telemetry.entity.TelemetrySource.SIMULATOR
          and n.type=com.quangkhai.vehicletracking_backend.reroute.entity.NotificationType.OFF_ROUTE_DETECTED
        """)
    List<TripNotificationEntity> findSimulationOffRouteEvents(Collection<Long> tripIds);

    @Query("""
            select count(n) from TripNotificationEntity n
            join n.trip t
            where t.id in :tripIds
              and n.type = :type
              and (n.attemptNumber is null or n.attemptNumber = t.attemptNumber)
              and n.createdAt >= :from
              and n.createdAt < :toExclusive
              and (:vehicleId is null or t.vehicle.id = :vehicleId)
              and (:driverId is null or t.driver.id = :driverId)
            """)
    long countForOperationalReport(@Param("tripIds") Collection<Long> tripIds,
                                    @Param("type") NotificationType type,
                                    @Param("from") Instant from,
                                    @Param("toExclusive") Instant toExclusive,
                                    @Param("vehicleId") Long vehicleId,
                                    @Param("driverId") Long driverId);

    @Query("""
            select n from TripNotificationEntity n
            join fetch n.trip t
            left join fetch n.simulationIncident i
            left join fetch i.reportedByDriver
            where t.id in :tripIds
              and n.createdAt >= :from
              and n.createdAt < :toExclusive
            order by n.createdAt desc, n.id desc
            """)
    List<TripNotificationEntity> findAllForOperationalReport(@Param("tripIds") Collection<Long> tripIds,
                                                               @Param("from") Instant from,
                                                               @Param("toExclusive") Instant toExclusive);
}
