package com.quangkhai.vehiceltracking_backend.repository;

import com.quangkhai.vehiceltracking_backend.entity.TrafficIncident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrafficIncidentRepository extends JpaRepository<TrafficIncident, Long> {
    List<TrafficIncident> findByActiveTrue();
}
