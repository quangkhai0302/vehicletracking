package com.quangkhai.vehicletracking_backend.reroute.repository;

import com.quangkhai.vehicletracking_backend.reroute.entity.TripNotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TripNotificationRepository extends JpaRepository<TripNotificationEntity, Long> {
    List<TripNotificationEntity> findTop50ByOrderByCreatedAtDescIdDesc();
    List<TripNotificationEntity> findTop50ByReadAtIsNullOrderByCreatedAtDescIdDesc();
    List<TripNotificationEntity> findAllByReadAtIsNullOrderByCreatedAtDescIdDesc();
    List<TripNotificationEntity> findAllByTripIdOrderByCreatedAtDescIdDesc(long tripId);
    Optional<TripNotificationEntity> findByDedupeKey(String dedupeKey);
}
