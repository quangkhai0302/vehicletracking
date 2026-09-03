package com.quangkhai.vehiceltracking_backend.service;

import com.quangkhai.vehiceltracking_backend.dto.StationDto;
import com.quangkhai.vehiceltracking_backend.entity.Station;
import com.quangkhai.vehiceltracking_backend.exception.StationConflictException;
import com.quangkhai.vehiceltracking_backend.exception.StationNotFoundException;
import com.quangkhai.vehiceltracking_backend.repository.RouteStationRepository;
import com.quangkhai.vehiceltracking_backend.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StationService {

    private final StationRepository stationRepository;
    private final RouteStationRepository routeStationRepository;

    public List<StationDto> getAllStations() {
        return stationRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public StationDto getStationById(Long id) {
        Station station = stationRepository.findById(id)
                .orElseThrow(() -> new StationNotFoundException("Không tìm thấy trạm dừng với ID: " + id));
        return toDto(station);
    }

    @Transactional
    public StationDto createStation(StationDto dto) {
        String normalizedCode = normalizeCode(dto.getCode());
        validateStationDto(dto);

        if (stationRepository.existsByCode(normalizedCode)) {
            throw new StationConflictException("Mã trạm đã tồn tại: " + normalizedCode);
        }

        Station station = Station.builder()
                .code(normalizedCode)
                .name(dto.getName().trim())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .address(dto.getAddress() != null && !dto.getAddress().trim().isEmpty() ? dto.getAddress().trim() : null)
                .radiusMeters(dto.getRadiusMeters())
                .stationType(dto.getStationType())
                .build();

        try {
            Station saved = stationRepository.save(station);
            stationRepository.flush();
            return toDto(saved);
        } catch (DataIntegrityViolationException ex) {
            if (isUniqueCodeViolation(ex, normalizedCode, null)) {
                throw new StationConflictException("Mã trạm đã tồn tại: " + normalizedCode);
            }
            throw new StationConflictException("Xung đột toàn vẹn dữ liệu trạm dừng");
        }
    }

    @Transactional
    public StationDto updateStation(Long id, StationDto dto) {
        Station station = stationRepository.findById(id)
                .orElseThrow(() -> new StationNotFoundException("Không tìm thấy trạm dừng với ID: " + id));

        String normalizedCode = normalizeCode(dto.getCode());
        validateStationDto(dto);

        if (stationRepository.existsByCodeAndIdNot(normalizedCode, id)) {
            throw new StationConflictException("Mã trạm đã tồn tại: " + normalizedCode);
        }

        station.setCode(normalizedCode);
        station.setName(dto.getName().trim());
        station.setLatitude(dto.getLatitude());
        station.setLongitude(dto.getLongitude());
        String address = dto.getAddress() != null ? dto.getAddress().trim() : null;
        if (address != null && address.isEmpty()) {
            address = null;
        }
        station.setAddress(address);
        station.setRadiusMeters(dto.getRadiusMeters());
        station.setStationType(dto.getStationType());

        try {
            Station saved = stationRepository.save(station);
            stationRepository.flush();
            return toDto(saved);
        } catch (DataIntegrityViolationException ex) {
            if (isUniqueCodeViolation(ex, normalizedCode, id)) {
                throw new StationConflictException("Mã trạm đã tồn tại: " + normalizedCode);
            }
            throw new StationConflictException("Xung đột toàn vẹn dữ liệu trạm dừng");
        }
    }

    @Transactional
    public void deleteStation(Long id) {
        Station station = stationRepository.findById(id)
                .orElseThrow(() -> new StationNotFoundException("Không tìm thấy trạm dừng với ID: " + id));

        if (routeStationRepository.existsByStationId(id)) {
            throw new StationConflictException("Không thể xóa trạm vì đang được sử dụng trong tuyến đường");
        }

        try {
            stationRepository.delete(station);
            stationRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new StationConflictException("Không thể xóa trạm vì có ràng buộc dữ liệu liên quan");
        }
    }

    private boolean isUniqueCodeViolation(DataIntegrityViolationException ex, String code, Long excludeId) {
        String normalizedCode = code.toUpperCase(Locale.ROOT);

        // 1. Kiểm tra nguyên nhân gốc rễ và SQLState / ConstraintName
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
                String constraint = cve.getConstraintName();
                if (constraint != null) {
                    String lowerConstraint = constraint.toLowerCase(Locale.ROOT);
                    if (lowerConstraint.contains("code") || lowerConstraint.contains("stations") || lowerConstraint.contains("uk_") || lowerConstraint.contains("unique")) {
                        return true;
                    }
                }
            }
            if (cause instanceof SQLException sqlEx) {
                String sqlState = sqlEx.getSQLState();
                // 23505: Unique constraint violation chuẩn trong ANSI SQL (H2, PostgreSQL)
                if ("23505".equals(sqlState)) {
                    String sqlMsg = sqlEx.getMessage() != null ? sqlEx.getMessage().toLowerCase(Locale.ROOT) : "";
                    if (sqlMsg.contains("code") || sqlMsg.contains(code.toLowerCase(Locale.ROOT))) {
                        return true;
                    }
                    if (!sqlMsg.contains("fk") && !sqlMsg.contains("check")) {
                        return true;
                    }
                }
            }
            cause = cause.getCause();
        }

        // 2. Phân tích message: phải có đồng thời chỉ báo vi phạm unique VÀ trường code
        String msg = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        if (msg != null) {
            String lower = msg.toLowerCase(Locale.ROOT);
            boolean isUniqueConstraint = lower.contains("unique") || lower.contains("duplicate") || lower.contains("23505");
            boolean isCodeField = lower.contains("code") || lower.contains(code.toLowerCase(Locale.ROOT));
            if (isUniqueConstraint && isCodeField) {
                return true;
            }
        }

        // 3. Fallback cho race condition khi có giao dịch song song (loại trừ chính record đang update nếu excludeId != null)
        if (excludeId == null) {
            return stationRepository.existsByCode(normalizedCode);
        } else {
            return stationRepository.existsByCodeAndIdNot(normalizedCode, excludeId);
        }
    }

    public String normalizeCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("Mã trạm không được để trống");
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Mã trạm không được để trống");
        }
        if (normalized.length() > 50) {
            throw new IllegalArgumentException("Mã trạm không được vượt quá 50 ký tự");
        }
        return normalized;
    }

    public void validateStationDto(StationDto dto) {
        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Tên trạm không được để trống");
        }
        if (dto.getName().trim().length() > 150) {
            throw new IllegalArgumentException("Tên trạm không được vượt quá 150 ký tự");
        }
        if (dto.getLatitude() == null || !Double.isFinite(dto.getLatitude()) || dto.getLatitude() < -90.0 || dto.getLatitude() > 90.0) {
            throw new IllegalArgumentException("Vĩ độ phải từ -90 đến 90");
        }
        if (dto.getLongitude() == null || !Double.isFinite(dto.getLongitude()) || dto.getLongitude() < -180.0 || dto.getLongitude() > 180.0) {
            throw new IllegalArgumentException("Kinh độ phải từ -180 đến 180");
        }
        if (dto.getAddress() != null && dto.getAddress().length() > 255) {
            throw new IllegalArgumentException("Địa chỉ không được vượt quá 255 ký tự");
        }
        if (dto.getRadiusMeters() == null || !Double.isFinite(dto.getRadiusMeters()) || dto.getRadiusMeters() < 30.0 || dto.getRadiusMeters() > 150.0) {
            throw new IllegalArgumentException("Bán kính check-in phải từ 30 đến 150 mét");
        }
        if (dto.getStationType() == null) {
            throw new IllegalArgumentException("Loại trạm không được để trống");
        }
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
