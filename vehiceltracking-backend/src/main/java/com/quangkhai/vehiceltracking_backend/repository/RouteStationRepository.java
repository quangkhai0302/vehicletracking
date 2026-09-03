package com.quangkhai.vehiceltracking_backend.repository;

import com.quangkhai.vehiceltracking_backend.entity.RouteStation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RouteStationRepository extends JpaRepository<RouteStation, Long> {
    List<RouteStation> findByRouteIdOrderByStopOrderAsc(Long routeId);
    void deleteByRouteId(Long routeId);
    boolean existsByStationId(Long stationId);
}
