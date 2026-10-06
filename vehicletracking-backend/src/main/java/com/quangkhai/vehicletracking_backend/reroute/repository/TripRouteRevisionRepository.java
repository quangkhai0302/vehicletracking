package com.quangkhai.vehicletracking_backend.reroute.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.quangkhai.vehicletracking_backend.reroute.entity.TripRouteRevisionEntity;

public interface TripRouteRevisionRepository extends JpaRepository<TripRouteRevisionEntity, Long> {
    List<TripRouteRevisionEntity> findAllByTripIdOrderByRevisionNumberDesc(long tripId);
    Optional<TripRouteRevisionEntity> findTopByTripIdAndStatusOrderByRevisionNumberDesc(long tripId, com.quangkhai.vehicletracking_backend.reroute.entity.RouteRevisionStatus status);
    int countByTripId(long tripId);
    @org.springframework.data.jpa.repository.Query("""
        select r from TripRouteRevisionEntity r join fetch r.trip
        where r.trip.id in :tripIds and (r.simulationAttemptNumber is not null
          or exists(select n.id from TripNotificationEntity n where n.revision=r
            and n.source=com.quangkhai.vehicletracking_backend.telemetry.entity.TelemetrySource.SIMULATOR
            and n.attemptNumber is not null))
        order by r.revisionNumber asc
        """)
    List<TripRouteRevisionEntity> findAllForSimulationReport(java.util.Collection<Long> tripIds);
}
