package com.quangkhai.vehicletracking_backend.station.service;

import com.quangkhai.vehicletracking_backend.station.dto.StationResponse;
import com.quangkhai.vehicletracking_backend.station.dto.StationUpsertRequest;
import com.quangkhai.vehicletracking_backend.station.entity.StationEntity;
import com.quangkhai.vehicletracking_backend.station.repository.StationRepository;
import com.quangkhai.vehicletracking_backend.route.service.RouteService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
public class StationService {

    private final StationRepository stationRepository;
    private final RouteService routeService;

    public StationService(StationRepository stationRepository, RouteService routeService) {
        this.stationRepository = stationRepository;
        this.routeService = routeService;
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
        String name = normalizeName(request.name());
        String address = normalizeAddress(request.address());
        boolean nameChanged = !Objects.equals(station.getName(), name);
        boolean coordinatesChanged = coordinateChanged(station.getLatitude(), request.latitude())
                || coordinateChanged(station.getLongitude(), request.longitude());
        station.updateDetails(
                name,
                address,
                request.latitude(),
                request.longitude(),
                request.checkinRadiusMeters()
        );
        if (nameChanged || coordinatesChanged) {
            routeService.refreshRoutesUsingStation(id, coordinatesChanged);
        }
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

    private boolean coordinateChanged(BigDecimal current, BigDecimal updated) {
        if (current == null || updated == null) return !Objects.equals(current, updated);
        return current.compareTo(updated) != 0;
    }
}
