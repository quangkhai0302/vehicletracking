package com.quangkhai.vehicletracking_backend.dispatch.repository;

import com.quangkhai.vehicletracking_backend.dispatch.entity.DispatchOfferStatus;
import com.quangkhai.vehicletracking_backend.dispatch.entity.TripDispatchOfferEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripDispatchOfferRepository extends JpaRepository<TripDispatchOfferEntity, UUID> {
    Optional<TripDispatchOfferEntity> findByDispatchTripIdAndStatus(long tripId, DispatchOfferStatus status);
    List<TripDispatchOfferEntity> findAllByCandidateDriverIdAndStatusOrderByExpiresAtAsc(long driverId, DispatchOfferStatus status);
    List<TripDispatchOfferEntity> findAllByDispatchTripIdOrderByOfferedAtAsc(long tripId);
    boolean existsByCandidateDriverIdAndStatus(long driverId, DispatchOfferStatus status);
}
