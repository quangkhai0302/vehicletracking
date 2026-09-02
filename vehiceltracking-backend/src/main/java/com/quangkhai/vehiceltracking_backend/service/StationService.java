package com.quangkhai.vehiceltracking_backend.service;

import com.quangkhai.vehiceltracking_backend.dto.StationDto;
import com.quangkhai.vehiceltracking_backend.entity.Station;
import com.quangkhai.vehiceltracking_backend.enums.StationType;
import com.quangkhai.vehiceltracking_backend.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StationService {

    private final StationRepository stationRepository;

    public List<StationDto> getAllStations() {
        return stationRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public StationDto getStationById(Long id) {
        Station station = stationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy trạm dừng với ID: " + id));
        return toDto(station);
    }

    @Transactional
    public StationDto createStation(StationDto dto) {
        if (stationRepository.existsByCode(dto.getCode())) {
            throw new IllegalArgumentException("Mã trạm đã tồn tại: " + dto.getCode());
        }

        Station station = Station.builder()
                .code(dto.getCode().trim().toUpperCase())
                .name(dto.getName().trim())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .address(dto.getAddress())
                .radiusMeters(dto.getRadiusMeters() != null ? dto.getRadiusMeters() : 60.0)
                .stationType(dto.getStationType() != null ? dto.getStationType() : StationType.STOP)
                .build();

        Station saved = stationRepository.save(station);
        return toDto(saved);
    }

    @Transactional
    public StationDto updateStation(Long id, StationDto dto) {
        Station station = stationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy trạm dừng với ID: " + id));

        if (!station.getCode().equalsIgnoreCase(dto.getCode()) && stationRepository.existsByCode(dto.getCode())) {
            throw new IllegalArgumentException("Mã trạm đã tồn tại: " + dto.getCode());
        }

        station.setCode(dto.getCode().trim().toUpperCase());
        station.setName(dto.getName().trim());
        station.setLatitude(dto.getLatitude());
        station.setLongitude(dto.getLongitude());
        station.setAddress(dto.getAddress());
        if (dto.getRadiusMeters() != null) {
            station.setRadiusMeters(dto.getRadiusMeters());
        }
        if (dto.getStationType() != null) {
            station.setStationType(dto.getStationType());
        }

        return toDto(stationRepository.save(station));
    }

    @Transactional
    public void deleteStation(Long id) {
        if (!stationRepository.existsById(id)) {
            throw new IllegalArgumentException("Không tìm thấy trạm dừng với ID: " + id);
        }
        stationRepository.deleteById(id);
    }

    public StationDto toDto(Station s) {
        return StationDto.builder()
                .id(s.getId())
                .code(s.getCode())
                .name(s.getName())
                .latitude(s.getLatitude())
                .longitude(s.getLongitude())
                .address(s.getAddress())
                .radiusMeters(s.getRadiusMeters())
                .stationType(s.getStationType())
                .createdAt(s.getCreatedAt())
                .build();
    }
}
