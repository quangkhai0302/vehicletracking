package com.quangkhai.vehicletracking_backend.assignment.repository;

import com.quangkhai.vehicletracking_backend.assignment.entity.TripAssignmentRequestEntity;
import com.quangkhai.vehicletracking_backend.assignment.entity.TripAssignmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface TripAssignmentRequestRepository extends JpaRepository<TripAssignmentRequestEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TripAssignmentRequestEntity r where r.id = :id")
    Optional<TripAssignmentRequestEntity> findLockedById(@Param("id") UUID id);
    @EntityGraph(attributePaths = {"candidateDriver", "trip"})
    Optional<TripAssignmentRequestEntity> findFirstByTripIdAndStatusOrderByRequestedAtDescIdDesc(long tripId, TripAssignmentStatus status);
    @EntityGraph(attributePaths = {"candidateDriver", "trip"})
    List<TripAssignmentRequestEntity> findAllByTripIdOrderByRequestedAtDescIdDesc(long tripId);
    @EntityGraph(attributePaths = {"candidateDriver", "trip"})
    Optional<TripAssignmentRequestEntity> findFirstByTripIdAndCandidateDriverIdAndStatusOrderByRespondedAtDescIdDesc(
            long tripId, long candidateDriverId, TripAssignmentStatus status);
    @EntityGraph(attributePaths = {"candidateDriver", "trip"})
    List<TripAssignmentRequestEntity> findAllByTripIdInOrderByRequestedAtDescIdDesc(Collection<Long> tripIds);
    @EntityGraph(attributePaths = {"candidateDriver", "trip"})
    List<TripAssignmentRequestEntity> findAllByTripIdInAndStatusOrderByRequestedAtDescIdDesc(
            Collection<Long> tripIds, TripAssignmentStatus status);
    @EntityGraph(attributePaths = {"trip", "trip.route"})
    List<TripAssignmentRequestEntity> findAllByCandidateDriverIdAndStatusOrderByRequestedAtDescIdDesc(long driverId, TripAssignmentStatus status, Pageable pageable);
    boolean existsByCandidateDriverIdAndStatus(long driverId, TripAssignmentStatus status);
    boolean existsByTripId(long tripId);
}
