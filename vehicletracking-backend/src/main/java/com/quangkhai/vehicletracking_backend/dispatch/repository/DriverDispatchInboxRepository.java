package com.quangkhai.vehicletracking_backend.dispatch.repository;

import com.quangkhai.vehicletracking_backend.dispatch.entity.DriverDispatchInboxEntity;
import com.quangkhai.vehicletracking_backend.dispatch.entity.DriverInboxKind;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface DriverDispatchInboxRepository extends JpaRepository<DriverDispatchInboxEntity, Long> {
    List<DriverDispatchInboxEntity> findByRecipientDriverIdOrderByCreatedAtDescIdDesc(long driverId, Pageable page);
    @Query("""
            select i from DriverDispatchInboxEntity i
            where i.recipientDriverId = :driverId
              and i.dismissedAt is null
              and (i.kind in :kinds or (i.kind = :unassignedKind and exists
                   (select t.id from TripEntity t where t.id = i.tripId and t.schedule is null)))
            order by i.createdAt desc, i.id desc
            """)
    List<DriverDispatchInboxEntity> findAssignmentInbox(long driverId, List<DriverInboxKind> kinds,
                                                       DriverInboxKind unassignedKind, Pageable page);
    Optional<DriverDispatchInboxEntity> findByIdAndRecipientDriverIdAndDismissedAtIsNull(long id, long driverId);
    boolean existsByDedupeKey(String dedupeKey);
}
