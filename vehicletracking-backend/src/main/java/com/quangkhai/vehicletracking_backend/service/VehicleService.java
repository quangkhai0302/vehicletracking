package com.quangkhai.vehiceltracking_backend.service;

import com.quangkhai.vehiceltracking_backend.dto.VehicleDto;
import com.quangkhai.vehiceltracking_backend.entity.Vehicle;
import com.quangkhai.vehiceltracking_backend.enums.VehicleStatus;
import com.quangkhai.vehiceltracking_backend.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository vehicleRepository;

    public List<VehicleDto> getAllVehicles() {
        return vehicleRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public VehicleDto getVehicleById(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy xe với ID: " + id));
        return toDto(vehicle);
    }

    @Transactional
    public VehicleDto createVehicle(VehicleDto dto) {
        if (vehicleRepository.existsByPlateNumber(dto.getPlateNumber())) {
            throw new IllegalArgumentException("Biển số xe đã tồn tại: " + dto.getPlateNumber());
        }

        Vehicle vehicle = Vehicle.builder()
                .plateNumber(dto.getPlateNumber().trim().toUpperCase())
                .model(dto.getModel())
                .status(dto.getStatus() != null ? dto.getStatus() : VehicleStatus.IDLE)
                .currentLatitude(dto.getCurrentLatitude())
                .currentLongitude(dto.getCurrentLongitude())
                .currentSpeed(dto.getCurrentSpeed() != null ? dto.getCurrentSpeed() : 0.0)
                .currentHeading(dto.getCurrentHeading() != null ? dto.getCurrentHeading() : 0.0)
                .build();

        return toDto(vehicleRepository.save(vehicle));
    }

    @Transactional
    public Vehicle updateLocation(Long vehicleId, Double lat, Double lng, Double speed, Double heading) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy xe với ID: " + vehicleId));

        vehicle.setCurrentLatitude(lat);
        vehicle.setCurrentLongitude(lng);
        vehicle.setCurrentSpeed(speed);
        vehicle.setCurrentHeading(heading);
        vehicle.setLastUpdatedAt(LocalDateTime.now());

        return vehicleRepository.save(vehicle);
    }

    @Transactional
    public void deleteVehicle(Long id) {
        if (!vehicleRepository.existsById(id)) {
            throw new IllegalArgumentException("Không tìm thấy xe với ID: " + id);
        }
        vehicleRepository.deleteById(id);
    }

    public VehicleDto toDto(Vehicle v) {
        return VehicleDto.builder()
                .id(v.getId())
                .plateNumber(v.getPlateNumber())
                .model(v.getModel())
                .status(v.getStatus())
                .currentLatitude(v.getCurrentLatitude())
                .currentLongitude(v.getCurrentLongitude())
                .currentSpeed(v.getCurrentSpeed())
                .currentHeading(v.getCurrentHeading())
                .lastUpdatedAt(v.getLastUpdatedAt())
                .build();
    }
}
