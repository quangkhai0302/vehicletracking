package com.quangkhai.vehicletracking_backend.reroute.repository;

import com.quangkhai.vehicletracking_backend.reroute.entity.TripNotificationEntity;
import com.quangkhai.vehicletracking_backend.reroute.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TripNotificationRepository extends JpaRepository<TripNotificationEntity, Long> {
    List<TripNotificationEntity> findTop50ByDismissedAtIsNullOrderByCreatedAtDescIdDesc();
    List<TripNotificationEntity> findTop50ByReadAtIsNullAndDismissedAtIsNullOrderByCreatedAtDescIdDesc();
    long countByReadAtIsNullAndDismissedAtIsNull();
    List<TripNotificationEntity> findAllByReadAtIsNullAndDismissedAtIsNullOrderByCreatedAtDescIdDesc();
    List<TripNotificationEntity> findAllByTripIdOrderByCreatedAtDescIdDesc(long tripId);
    Optional<TripNotificationEntity> findByDedupeKey(String dedupeKey);

    @Query("""
            select count(n) from TripNotificationEntity n
            join n.trip t
            where t.id in :tripIds
              and n.type = :type
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
}
