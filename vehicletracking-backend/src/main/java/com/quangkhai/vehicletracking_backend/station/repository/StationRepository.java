package com.quangkhai.vehicletracking_backend.station.repository;

import com.quangkhai.vehicletracking_backend.station.entity.StationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StationRepository extends JpaRepository<StationEntity, Long> {

    List<StationEntity> findAllByActiveTrueOrderByNameAscIdAsc();

    Optional<StationEntity> findByIdAndActiveTrue(long id);
}
