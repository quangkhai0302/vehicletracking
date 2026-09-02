package com.quangkhai.vehiceltracking_backend.service;

import com.quangkhai.vehiceltracking_backend.dto.RouteRequestDto;
import com.quangkhai.vehiceltracking_backend.dto.RouteResponseDto;
import com.quangkhai.vehiceltracking_backend.dto.RouteStationDto;
import com.quangkhai.vehiceltracking_backend.entity.Route;
import com.quangkhai.vehiceltracking_backend.entity.RouteStation;
import com.quangkhai.vehiceltracking_backend.entity.Station;
import com.quangkhai.vehiceltracking_backend.repository.RouteRepository;
import com.quangkhai.vehiceltracking_backend.repository.StationRepository;
import com.quangkhai.vehiceltracking_backend.util.GeoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RouteService {

    private final RouteRepository routeRepository;
    private final StationRepository stationRepository;
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
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tuyến đường với ID: " + id));
        return toResponseDto(route);
    }

    @Transactional
    public RouteResponseDto createRoute(RouteRequestDto request) {
        if (request.getStationIds() == null || request.getStationIds().size() < 2) {
            throw new IllegalArgumentException("Tuyến đường phải có ít nhất 2 trạm dừng (trạm đầu và trạm cuối)");
        }

        String code = request.getCode();
        if (code == null || code.trim().isEmpty()) {
            code = "ROUTE-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        } else if (routeRepository.existsByCode(code.trim())) {
            throw new IllegalArgumentException("Mã tuyến đường đã tồn tại: " + code);
        }

        Route route = Route.builder()
                .code(code.trim().toUpperCase())
                .name(request.getName() != null ? request.getName().trim() : "Tuyến " + code)
                .description(request.getDescription())
                .build();

        List<Station> stations = new ArrayList<>();
        for (Long stationId : request.getStationIds()) {
            Station station = stationRepository.findById(stationId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy trạm với ID: " + stationId));
            stations.add(station);
        }

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
        route.setRouteStations(routeStations);

        Route saved = routeRepository.save(route);
        return toResponseDto(saved);
    }

    @Transactional
    public void deleteRoute(Long id) {
        if (!routeRepository.existsById(id)) {
            throw new IllegalArgumentException("Không tìm thấy tuyến đường với ID: " + id);
        }
        routeRepository.deleteById(id);
    }

    public RouteResponseDto toResponseDto(Route route) {
        List<RouteStationDto> stationDtos = route.getRouteStations().stream()
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
