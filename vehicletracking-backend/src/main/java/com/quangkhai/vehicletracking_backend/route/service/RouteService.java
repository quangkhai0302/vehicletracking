package com.quangkhai.vehicletracking_backend.route.service;

import com.quangkhai.vehicletracking_backend.route.dto.RouteCreateRequest;
import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
import com.quangkhai.vehicletracking_backend.route.dto.RouteSummaryResponse;
import com.quangkhai.vehicletracking_backend.route.entity.RouteEntity;
import com.quangkhai.vehicletracking_backend.route.entity.RouteSectionEntity;
import com.quangkhai.vehicletracking_backend.route.entity.RouteStopEntity;
import com.quangkhai.vehicletracking_backend.route.entity.RouteTransportMode;
import com.quangkhai.vehicletracking_backend.route.entity.RoutingProviderName;
import com.quangkhai.vehicletracking_backend.route.error.RouteErrorCode;
import com.quangkhai.vehicletracking_backend.route.error.RouteOperationException;
import com.quangkhai.vehicletracking_backend.route.provider.CalculatedRoute;
import com.quangkhai.vehicletracking_backend.route.provider.CalculatedSection;
import com.quangkhai.vehicletracking_backend.route.provider.RoutingProvider;
import com.quangkhai.vehicletracking_backend.route.provider.RoutingWaypoint;
import com.quangkhai.vehicletracking_backend.route.repository.RouteRepository;
import com.quangkhai.vehicletracking_backend.station.entity.StationEntity;
import com.quangkhai.vehicletracking_backend.station.repository.StationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RouteService {

    private final RouteRepository routeRepository;
    private final StationRepository stationRepository;
    private final RoutingProvider routingProvider;
    private final RoutePersistenceService routePersistenceService;

    public RouteService(
            RouteRepository routeRepository,
            StationRepository stationRepository,
            RoutingProvider routingProvider,
            RoutePersistenceService routePersistenceService
    ) {
        this.routeRepository = routeRepository;
        this.stationRepository = stationRepository;
        this.routingProvider = routingProvider;
        this.routePersistenceService = routePersistenceService;
    }

    public RouteDetailResponse create(RouteCreateRequest request) {
        String normalizedName = normalizeName(request.name());
        validateStops(request.stops());

        Set<Long> uniqueStationIds = request.stops().stream()
                .map(RouteCreateRequest.RouteStopInput::stationId)
                .collect(Collectors.toSet());

        List<StationEntity> activeStations = stationRepository.findAllByIdInAndActiveTrue(uniqueStationIds);
        Map<Long, StationEntity> stationMap = activeStations.stream()
                .collect(Collectors.toMap(StationEntity::getId, s -> s));

        if (stationMap.size() < uniqueStationIds.size()) {
            List<Long> unavailableIds = uniqueStationIds.stream()
                    .filter(id -> !stationMap.containsKey(id))
                    .sorted()
                    .toList();
            throw new RouteOperationException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    RouteErrorCode.ROUTE_STATION_UNAVAILABLE,
                    "Stations are unavailable or inactive: " + unavailableIds
            );
        }

        List<RoutingWaypoint> waypoints = new ArrayList<>(request.stops().size());
        for (int i = 0; i < request.stops().size(); i++) {
            var stopInput = request.stops().get(i);
            StationEntity s = stationMap.get(stopInput.stationId());
            waypoints.add(new RoutingWaypoint(
                    s.getId(),
                    s.getName(),
                    s.getLatitude(),
                    s.getLongitude(),
                    i + 1,
                    stopInput.dwellDurationSeconds()
            ));
        }

        // Call provider outside database transaction
        CalculatedRoute calculatedRoute = routingProvider.calculate(waypoints);

        long totalDistance = 0;
        long travelDuration = 0;
        long baseTravelDuration = 0;

        for (CalculatedSection cs : calculatedRoute.sections()) {
            totalDistance += cs.distanceMeters();
            travelDuration += cs.travelDurationSeconds();
            baseTravelDuration += cs.baseTravelDurationSeconds();
        }

        long totalDwell = 0;
        for (int i = 1; i < waypoints.size() - 1; i++) {
            totalDwell += waypoints.get(i).dwellDurationSeconds();
        }

        long tripDuration = travelDuration + totalDwell;
        Instant calculatedAt = Instant.now();

        RouteEntity route = new RouteEntity(
                normalizedName,
                RouteTransportMode.CAR,
                RoutingProviderName.HERE,
                totalDistance,
                travelDuration,
                baseTravelDuration,
                totalDwell,
                tripDuration,
                calculatedRoute.estimatedDepartureAt(),
                calculatedAt
        );

        for (int i = 0; i < waypoints.size(); i++) {
            RoutingWaypoint wp = waypoints.get(i);
            StationEntity station = stationMap.get(wp.stationId());
            route.addStop(new RouteStopEntity(
                    station,
                    wp.sequenceNumber(),
                    wp.stationName(),
                    wp.latitude(),
                    wp.longitude(),
                    wp.dwellDurationSeconds()
            ));
        }

        for (CalculatedSection cs : calculatedRoute.sections()) {
            route.addSection(new RouteSectionEntity(
                    cs.sectionSequence(),
                    cs.destinationStopSequence(),
                    cs.encodedPolyline(),
                    cs.distanceMeters(),
                    cs.travelDurationSeconds(),
                    cs.baseTravelDurationSeconds()
            ));
        }

        RouteEntity saved = routePersistenceService.persistRoute(route);
        return RouteDetailResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<RouteSummaryResponse> findAll() {
        return routeRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(RouteSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public RouteDetailResponse findById(Long id) {
        RouteEntity route = routeRepository.findById(id)
                .orElseThrow(() -> new RouteOperationException(
                        HttpStatus.NOT_FOUND,
                        RouteErrorCode.ROUTE_NOT_FOUND,
                        "Route " + id + " was not found"
                ));
        return RouteDetailResponse.from(route);
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new RouteOperationException(
                    HttpStatus.BAD_REQUEST,
                    RouteErrorCode.ROUTE_VALIDATION_FAILED,
                    "Route name must not be blank"
            );
        }
        return name.trim();
    }

    private void validateStops(List<RouteCreateRequest.RouteStopInput> stops) {
        if (stops == null || stops.size() < 2 || stops.size() > 50) {
            throw new RouteOperationException(
                    HttpStatus.BAD_REQUEST,
                    RouteErrorCode.ROUTE_VALIDATION_FAILED,
                    "Route must have between 2 and 50 stops"
            );
        }

        for (int i = 0; i < stops.size(); i++) {
            var stop = stops.get(i);
            if (stop == null || stop.stationId() == null || stop.dwellDurationSeconds() == null) {
                throw new RouteOperationException(
                        HttpStatus.BAD_REQUEST,
                        RouteErrorCode.ROUTE_VALIDATION_FAILED,
                        "Stop at position " + (i + 1) + " must not be null or incomplete"
                );
            }
            if (stop.dwellDurationSeconds() < 0 || stop.dwellDurationSeconds() > 3600) {
                throw new RouteOperationException(
                        HttpStatus.BAD_REQUEST,
                        RouteErrorCode.ROUTE_VALIDATION_FAILED,
                        "Stop at position " + (i + 1) + " dwellDurationSeconds must be between 0 and 3600"
                );
            }
        }

        if (stops.get(0).dwellDurationSeconds() != 0 || stops.get(stops.size() - 1).dwellDurationSeconds() != 0) {
            throw new RouteOperationException(
                    HttpStatus.BAD_REQUEST,
                    RouteErrorCode.ROUTE_VALIDATION_FAILED,
                    "START and END stops must have dwellDurationSeconds equal to 0"
            );
        }

        for (int i = 1; i < stops.size(); i++) {
            if (stops.get(i).stationId().equals(stops.get(i - 1).stationId())) {
                throw new RouteOperationException(
                        HttpStatus.BAD_REQUEST,
                        RouteErrorCode.ROUTE_VALIDATION_FAILED,
                        "Consecutive stops cannot reference the same station"
                );
            }
        }
    }
}
