package com.quangkhai.vehiceltracking_backend.repository;

import com.quangkhai.vehiceltracking_backend.entity.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StationRepository extends JpaRepository<Station, Long> {
    Optional<Station> findByCode(String code);
    boolean existsByCode(String code);
}
