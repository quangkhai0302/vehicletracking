package com.quangkhai.vehicletracking_backend.dispatch.repository;

import com.quangkhai.vehicletracking_backend.dispatch.entity.TripDispatchEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TripDispatchEventRepository extends JpaRepository<TripDispatchEventEntity, Long> {
    List<TripDispatchEventEntity> findTop100ByDispatchTripIdOrderByCreatedAtDescIdDesc(long tripId);
}
