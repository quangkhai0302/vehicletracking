package com.quangkhai.vehicletracking_backend.dispatch.repository;

import com.quangkhai.vehicletracking_backend.dispatch.entity.DriverDispatchInboxEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DriverDispatchInboxRepository extends JpaRepository<DriverDispatchInboxEntity, Long> {
    List<DriverDispatchInboxEntity> findByRecipientDriverIdOrderByCreatedAtDescIdDesc(long driverId, Pageable page);
    Optional<DriverDispatchInboxEntity> findByIdAndRecipientDriverId(long id, long driverId);
    boolean existsByDedupeKey(String dedupeKey);
}
