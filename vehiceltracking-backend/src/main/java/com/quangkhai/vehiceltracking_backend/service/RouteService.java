package com.quangkhai.vehiceltracking_backend.service;

import com.quangkhai.vehiceltracking_backend.dto.RouteRequestDto;
import com.quangkhai.vehiceltracking_backend.dto.RouteResponseDto;
import com.quangkhai.vehiceltracking_backend.dto.RouteStationDto;
import com.quangkhai.vehiceltracking_backend.entity.Route;
import com.quangkhai.vehiceltracking_backend.entity.RouteStation;
import com.quangkhai.vehiceltracking_backend.entity.Station;
import com.quangkhai.vehiceltracking_backend.enums.StationType;
import com.quangkhai.vehiceltracking_backend.exception.RouteConflictException;
import com.quangkhai.vehiceltracking_backend.exception.RouteNotFoundException;
import com.quangkhai.vehiceltracking_backend.repository.RouteRepository;
import com.quangkhai.vehiceltracking_backend.repository.StationRepository;
import com.quangkhai.vehiceltracking_backend.repository.TripRepository;
import com.quangkhai.vehiceltracking_backend.util.GeoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RouteService {

    private final RouteRepository routeRepository;
    private final StationRepository stationRepository;
    private final TripRepository tripRepository;
    private final StationService stationService;

    private static final double AVERAGE_URBAN_SPEED_KMH = 35.0; // Vận tốc trung bình trong đô thị
    private static final double STATION_DWELL_TIME_MINUTES = 1.5; // Thời gian dừng đón/trả khách mỗi trạm

    public List<RouteResponseDto> getAllRoutes() {
        return routeRepository.findAll().stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    public RouteResponseDto getRouteById(Long id) {
        Route route = routeRepository.findById(id)
                .orElseThrow(() -> new RouteNotFoundException("Không tìm thấy tuyến đường với ID: " + id));
        return toResponseDto(route);
    }

    @Transactional
    public RouteResponseDto createRoute(RouteRequestDto request) {
        validateBasicDto(request);

        String normalizedCode;
        if (request.getCode() == null || request.getCode().trim().isEmpty()) {
            normalizedCode = "ROUTE-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        } else {
            normalizedCode = normalizeCode(request.getCode());
            if (routeRepository.existsByCode(normalizedCode)) {
                throw new RouteConflictException("Mã tuyến đã tồn tại: " + normalizedCode);
            }
        }

        List<Station> stations = validateAndFetchStations(request.getStationIds());

        Route route = Route.builder()
                .code(normalizedCode)
                .name(request.getName().trim())
                .description(request.getDescription() != null && !request.getDescription().trim().isEmpty() ? request.getDescription().trim() : null)
                .build();

        applyStationsAndMetrics(route, stations);

        try {
            Route saved = routeRepository.save(route);
            routeRepository.flush();
            return toResponseDto(saved);
        } catch (DataIntegrityViolationException ex) {
            if (isUniqueCodeViolation(ex, normalizedCode, null)) {
                throw new RouteConflictException("Mã tuyến đã tồn tại: " + normalizedCode);
            }
            throw new RouteConflictException("Xung đột toàn vẹn dữ liệu tuyến đường");
        }
    }

    @Transactional
    public RouteResponseDto updateRoute(Long id, RouteRequestDto request) {
        Route route = routeRepository.findById(id)
                .orElseThrow(() -> new RouteNotFoundException("Không tìm thấy tuyến đường với ID: " + id));

        if (tripRepository.existsByRouteId(id)) {
            throw new RouteConflictException("Không thể sửa hoặc xóa tuyến đã được gán cho chuyến đi.");
        }

        validateBasicDto(request);

        String normalizedCode;
        if (request.getCode() == null || request.getCode().trim().isEmpty()) {
            normalizedCode = route.getCode();
        } else {
            normalizedCode = normalizeCode(request.getCode());
            if (routeRepository.existsByCodeAndIdNot(normalizedCode, id)) {
                throw new RouteConflictException("Mã tuyến đã tồn tại: " + normalizedCode);
            }
        }

        List<Station> stations = validateAndFetchStations(request.getStationIds());

        route.setCode(normalizedCode);
        route.setName(request.getName().trim());
        route.setDescription(request.getDescription() != null && !request.getDescription().trim().isEmpty() ? request.getDescription().trim() : null);

        try {
            // Xóa các RouteStation cũ và flush trước để tránh vi phạm unique (route_id, stop_order)
            route.getRouteStations().clear();
            routeRepository.flush();

            // Áp dụng topology trạm mới và tính lại metrics
            applyStationsAndMetrics(route, stations);

            Route saved = routeRepository.save(route);
            routeRepository.flush();
            return toResponseDto(saved);
        } catch (DataIntegrityViolationException ex) {
            if (isUniqueCodeViolation(ex, normalizedCode, id)) {
                throw new RouteConflictException("Mã tuyến đã tồn tại: " + normalizedCode);
            }
            throw new RouteConflictException("Xung đột toàn vẹn dữ liệu tuyến đường");
        }
    }

    @Transactional
    public void deleteRoute(Long id) {
        Route route = routeRepository.findById(id)
                .orElseThrow(() -> new RouteNotFoundException("Không tìm thấy tuyến đường với ID: " + id));

        if (tripRepository.existsByRouteId(id)) {
            throw new RouteConflictException("Không thể sửa hoặc xóa tuyến đã được gán cho chuyến đi.");
        }

        try {
            routeRepository.delete(route);
            routeRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new RouteConflictException("Không thể xóa tuyến đường vì có dữ liệu ràng buộc liên quan");
        }
    }

    private void validateBasicDto(RouteRequestDto request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Tên tuyến đường không được để trống");
        }
        if (request.getName().trim().length() > 150) {
            throw new IllegalArgumentException("Tên tuyến đường không được vượt quá 150 ký tự");
        }
        if (request.getCode() != null && request.getCode().trim().length() > 50) {
            throw new IllegalArgumentException("Mã tuyến đường không được vượt quá 50 ký tự");
        }
        if (request.getStationIds() == null || request.getStationIds().size() < 2) {
            throw new IllegalArgumentException("Tuyến đường phải có ít nhất 2 trạm dừng (trạm đầu và trạm cuối)");
        }
    }

    private List<Station> validateAndFetchStations(List<Long> stationIds) {
        List<Station> stations = new ArrayList<>();
        for (Long stationId : stationIds) {
            if (stationId == null) {
                throw new IllegalArgumentException("ID trạm trong danh sách không được để trống");
            }
            Station station = stationRepository.findById(stationId)
                    .orElseThrow(() -> new RouteNotFoundException("Không tìm thấy trạm với ID: " + stationId));
            stations.add(station);
        }

        // BR-002: Phần tử đầu phải là START, cuối là END, mọi phần tử ở giữa là STOP
        Station startStation = stations.get(0);
        Station endStation = stations.get(stations.size() - 1);

        if (startStation.getStationType() != StationType.START
                || endStation.getStationType() != StationType.END) {
            throw new IllegalArgumentException("Tuyến phải bắt đầu bằng trạm START, kết thúc bằng trạm END và các trạm giữa phải là STOP.");
        }

        for (int i = 1; i < stations.size() - 1; i++) {
            if (stations.get(i).getStationType() != StationType.STOP) {
                throw new IllegalArgumentException("Tuyến phải bắt đầu bằng trạm START, kết thúc bằng trạm END và các trạm giữa phải là STOP.");
            }
        }

        return stations;
    }

    private void applyStationsAndMetrics(Route route, List<Station> stations) {
        List<RouteStation> routeStations = new ArrayList<>();
        double totalDistanceKm = 0.0;
        double totalDurationMinutes = 0.0;

        for (int i = 0; i < stations.size(); i++) {
            Station current = stations.get(i);
            double distToNext = 0.0;
            double timeToNext = 0.0;

            if (i < stations.size() - 1) {
                Station next = stations.get(i + 1);
                distToNext = GeoUtil.calculateDistanceKm(
                        current.getLatitude(), current.getLongitude(),
                        next.getLatitude(), next.getLongitude()
                );
                timeToNext = (distToNext / AVERAGE_URBAN_SPEED_KMH) * 60.0 + STATION_DWELL_TIME_MINUTES;

                totalDistanceKm += distToNext;
                totalDurationMinutes += timeToNext;
            }

            RouteStation rs = RouteStation.builder()
                    .route(route)
                    .station(current)
                    .stopOrder(i + 1)
                    .distanceToNextKm(round(distToNext, 2))
                    .estimatedTimeToNextMinutes(round(timeToNext, 1))
                    .build();

            routeStations.add(rs);
        }

        route.setTotalDistanceKm(round(totalDistanceKm, 2));
        route.setEstimatedDurationMinutes(round(totalDurationMinutes, 1));
        route.getRouteStations().addAll(routeStations);
    }

    private boolean isUniqueCodeViolation(DataIntegrityViolationException ex, String code, Long excludeId) {
        String normalizedCode = code.toUpperCase(Locale.ROOT);

        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
                String constraint = cve.getConstraintName();
                if (constraint != null) {
                    String lowerConstraint = constraint.toLowerCase(Locale.ROOT);
                    if (lowerConstraint.contains("code") || lowerConstraint.contains("routes") || lowerConstraint.contains("uk_") || lowerConstraint.contains("unique")) {
                        return true;
                    }
                }
            }
            if (cause instanceof SQLException sqlEx) {
                String sqlState = sqlEx.getSQLState();
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

        String msg = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        if (msg != null) {
            String lower = msg.toLowerCase(Locale.ROOT);
            boolean isUniqueConstraint = lower.contains("unique") || lower.contains("duplicate") || lower.contains("23505");
            boolean isCodeField = lower.contains("code") || lower.contains(code.toLowerCase(Locale.ROOT));
            if (isUniqueConstraint && isCodeField) {
                return true;
            }
        }

        if (excludeId == null) {
            return routeRepository.existsByCode(normalizedCode);
        } else {
            return routeRepository.existsByCodeAndIdNot(normalizedCode, excludeId);
        }
    }

    public String normalizeCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("Mã tuyến đường không được để trống");
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Mã tuyến đường không được để trống");
        }
        if (normalized.length() > 50) {
            throw new IllegalArgumentException("Mã tuyến đường không được vượt quá 50 ký tự");
        }
        return normalized;
    }

    public RouteResponseDto toResponseDto(Route route) {
        List<RouteStationDto> stationDtos = route.getRouteStations().stream()
                .sorted(Comparator.comparing(RouteStation::getStopOrder))
                .map(rs -> RouteStationDto.builder()
                        .id(rs.getId())
                        .stopOrder(rs.getStopOrder())
                        .station(stationService.toDto(rs.getStation()))
                        .distanceToNextKm(rs.getDistanceToNextKm())
                        .estimatedTimeToNextMinutes(rs.getEstimatedTimeToNextMinutes())
                        .build())
                .collect(Collectors.toList());

        return RouteResponseDto.builder()
                .id(route.getId())
                .code(route.getCode())
                .name(route.getName())
                .description(route.getDescription())
                .totalDistanceKm(route.getTotalDistanceKm())
                .estimatedDurationMinutes(route.getEstimatedDurationMinutes())
                .stations(stationDtos)
                .createdAt(route.getCreatedAt())
                .build();
    }

    private double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}
