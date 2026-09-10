package com.quangkhai.vehicletracking_backend.station.service;

import com.quangkhai.vehicletracking_backend.station.dto.StationResponse;
import com.quangkhai.vehicletracking_backend.station.dto.StationUpsertRequest;
import com.quangkhai.vehicletracking_backend.station.entity.StationEntity;
import com.quangkhai.vehicletracking_backend.station.repository.StationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class StationService {

    private final StationRepository stationRepository;

    public StationService(StationRepository stationRepository) {
        this.stationRepository = stationRepository;
    }

    @Transactional(readOnly = true)
    public List<StationResponse> findAll() {
        return stationRepository.findAllByActiveTrueOrderByNameAscIdAsc().stream()
                .map(StationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public StationResponse findById(long id) {
        return StationResponse.from(findActiveStation(id));
    }

    @Transactional
    public StationResponse create(StationUpsertRequest request) {
        StationEntity station = new StationEntity(
                normalizeName(request.name()),
                normalizeAddress(request.address()),
                request.latitude(),
                request.longitude(),
                request.checkinRadiusMeters()
        );
        return StationResponse.from(stationRepository.save(station));
    }

    @Transactional
    public StationResponse update(long id, StationUpsertRequest request) {
        StationEntity station = findActiveStation(id);
        station.updateDetails(
                normalizeName(request.name()),
                normalizeAddress(request.address()),
                request.latitude(),
                request.longitude(),
                request.checkinRadiusMeters()
        );
        return StationResponse.from(station);
    }

    @Transactional
    public void delete(long id) {
        findActiveStation(id).deactivate();
    }

    private StationEntity findActiveStation(long id) {
        return stationRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> stationNotFound(id));
    }

    private ResponseStatusException stationNotFound(long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Station " + id + " was not found");
    }

    private String normalizeName(String name) {
        return name.trim();
    }

    private String normalizeAddress(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        return address.trim();
    }
}
