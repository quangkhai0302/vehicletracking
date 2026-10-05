package com.quangkhai.vehicletracking_backend.dispatch.repository;

import com.quangkhai.vehicletracking_backend.dispatch.entity.TripDispatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** Read access to retained dispatch history; no runtime dispatch scheduling. */
public interface TripDispatchRepository extends JpaRepository<TripDispatchEntity, Long> {}
