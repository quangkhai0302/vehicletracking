package com.quangkhai.vehicletracking_backend.route.service;

import com.quangkhai.vehicletracking_backend.route.dto.RouteCreateRequest;
import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
import com.quangkhai.vehicletracking_backend.route.dto.RouteSummaryResponse;
import com.quangkhai.vehicletracking_backend.route.entity.RouteEntity;
import com.quangkhai.vehicletracking_backend.route.entity.RouteSectionEntity;
import com.quangkhai.vehicletracking_backend.route.entity.RouteShapePointEntity;
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
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RouteService {

    private final RouteRepository routeRepository;
    private final StationRepository stationRepository;
    private final RoutingProvider routingProvider;
    private final RoutePersistenceService routePersistenceService;
    private final TripRepository tripRepository;

    public RouteService(
            RouteRepository routeRepository,
            StationRepository stationRepository,
            RoutingProvider routingProvider,
            RoutePersistenceService routePersistenceService,
            TripRepository tripRepository
    ) {
        this.routeRepository = routeRepository;
        this.stationRepository = stationRepository;
        this.routingProvider = routingProvider;
        this.routePersistenceService = routePersistenceService;
        this.tripRepository = tripRepository;
    }

    public RouteDetailResponse create(RouteCreateRequest request) {
        RouteEntity saved = routePersistenceService.persistRoute(buildRoute(request));
        return RouteDetailResponse.from(saved);
    }

    @Transactional
    public RouteDetailResponse update(long id, RouteCreateRequest request) {
        RouteEntity current = routeRepository.findLockedById(id).orElseThrow(() -> new RouteOperationException(
                HttpStatus.NOT_FOUND, RouteErrorCode.ROUTE_NOT_FOUND, "Route " + id + " was not found"));
        if (!current.isActive()) throw new RouteOperationException(HttpStatus.CONFLICT, RouteErrorCode.ROUTE_VALIDATION_FAILED,
                "Không thể sửa tuyến đã ngừng sử dụng.");
        if (tripRepository.existsByRouteId(id)) throw new RouteOperationException(HttpStatus.CONFLICT, RouteErrorCode.ROUTE_VALIDATION_FAILED,
                "Không thể sửa tuyến đã được dùng bởi chuyến; hãy tạo tuyến revision mới.");
        /*
         * Build everything before mutating the managed route.  Child rows use
         * unique (route_id, sequence) keys; replacing the collections in one
         * flush can make Hibernate insert the new rows before orphan removal
         * deletes the old rows, resulting in a 23505/HTTP 500.  Flush the
         * orphan removals first, then attach the freshly calculated snapshot.
         */
        RouteEntity replacement = buildRoute(request);
        return persistReplacement(current, replacement);
    }

    /**
     * Rebuilds active, unused route definitions that reference a station after
     * the station's name or coordinates change. Trip history is intentionally
     * excluded: trip stops keep their own immutable snapshots.
     */
    @Transactional
    public void refreshRoutesUsingStation(long stationId, boolean coordinatesChanged) {
        List<RouteEntity> candidates = routeRepository.findAllActiveByStationId(stationId);
        if (candidates.isEmpty()) return;

        for (RouteEntity candidate : candidates) {
            Long routeId = candidate.getId();
            if (routeId == null) continue;
            RouteEntity route = routeRepository.findLockedById(routeId).orElse(null);
            if (route == null || !route.isActive() || tripRepository.existsByRouteId(routeId)) continue;

            if (coordinatesChanged) {
                persistReplacement(route, buildRouteFromCurrentDefinition(route));
            } else {
                route.getStops().stream()
                        .filter(stop -> Objects.equals(stop.getStation().getId(), stationId))
                        .forEach(RouteStopEntity::refreshSnapshotFromStation);
            }
        }
    }

    @Transactional
    public void deactivate(long id) {
        RouteEntity route = routeRepository.findLockedById(id).orElseThrow(() -> new RouteOperationException(
                HttpStatus.NOT_FOUND, RouteErrorCode.ROUTE_NOT_FOUND, "Route " + id + " was not found"));
        if (!route.isActive()) return;
        if (tripRepository.existsByRouteIdAndStatusIn(id, List.of(TripStatus.SCHEDULED, TripStatus.IN_PROGRESS)))
            throw new RouteOperationException(HttpStatus.CONFLICT, RouteErrorCode.ROUTE_VALIDATION_FAILED,
                    "Không thể ngừng tuyến đang được sử dụng bởi chuyến chưa kết thúc.");
        route.deactivate();
    }

    @Transactional(readOnly = true)
    public List<RouteSummaryResponse> findAll() {
        return routeRepository.findAllByActiveTrueOrderByCreatedAtDescIdDesc().stream()
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

    /** Provider call and construction are kept in one reusable path for create/update. */
    private RouteEntity buildRoute(RouteCreateRequest request) {
        String normalizedName = normalizeName(request.name());
        validateStops(request.stops());
        Set<Long> uniqueStationIds = request.stops().stream().map(RouteCreateRequest.RouteStopInput::stationId).collect(Collectors.toSet());
        Map<Long, StationEntity> stationMap = stationRepository.findAllByIdInAndActiveTrue(uniqueStationIds).stream()
                .collect(Collectors.toMap(StationEntity::getId, s -> s));
        if (stationMap.size() < uniqueStationIds.size()) {
            List<Long> unavailableIds = uniqueStationIds.stream().filter(id -> !stationMap.containsKey(id)).sorted().toList();
            throw new RouteOperationException(HttpStatus.UNPROCESSABLE_ENTITY, RouteErrorCode.ROUTE_STATION_UNAVAILABLE,
                    "Stations are unavailable or inactive: " + unavailableIds);
        }
        List<RoutingWaypoint> waypoints = new ArrayList<>(request.stops().size());
        for (int i = 0; i < request.stops().size(); i++) {
            var input = request.stops().get(i); StationEntity station = stationMap.get(input.stationId());
            waypoints.add(new RoutingWaypoint(station.getId(), station.getName(), station.getLatitude(), station.getLongitude(), i + 1, input.dwellDurationSeconds()));
        }
        CalculatedRoute calculated = routingProvider.calculate(waypoints);
        long distance = calculated.sections().stream().mapToLong(CalculatedSection::distanceMeters).sum();
        long travel = calculated.sections().stream().mapToLong(CalculatedSection::travelDurationSeconds).sum();
        long base = calculated.sections().stream().mapToLong(CalculatedSection::baseTravelDurationSeconds).sum();
        long dwell = waypoints.subList(1, waypoints.size() - 1).stream().mapToLong(RoutingWaypoint::dwellDurationSeconds).sum();
        RouteEntity route = new RouteEntity(normalizedName, RouteTransportMode.CAR, RoutingProviderName.HERE, distance, travel, base,
                dwell, travel + dwell, calculated.estimatedDepartureAt(), Instant.now());
        waypoints.forEach(wp -> route.addStop(new RouteStopEntity(stationMap.get(wp.stationId()), wp.sequenceNumber(), wp.stationName(),
                wp.latitude(), wp.longitude(), wp.dwellDurationSeconds())));
        calculated.sections().forEach(cs -> route.addSection(new RouteSectionEntity(cs.sectionSequence(), cs.destinationStopSequence(),
                cs.encodedPolyline(), cs.distanceMeters(), cs.travelDurationSeconds(), cs.baseTravelDurationSeconds())));
        return route;
    }

    /** Recalculates a route while retaining its stop order, dwell times and shaping points. */
    private RouteEntity buildRouteFromCurrentDefinition(RouteEntity source) {
        List<RouteStopEntity> sourceStops = source.getStops();
        if (sourceStops.size() < 2) {
            throw new RouteOperationException(HttpStatus.BAD_REQUEST, RouteErrorCode.ROUTE_VALIDATION_FAILED,
                    "Route must have between 2 and 50 stops");
        }

        List<RoutingWaypoint> waypoints = new ArrayList<>();
        List<Integer> destinationStopSequences = new ArrayList<>();
        for (RouteStopEntity stop : sourceStops) {
            source.getShapingPoints().stream()
                    .filter(point -> point.getDestinationStopSequence() == stop.getSequenceNumber())
                    .forEach(point -> {
                        waypoints.add(new RoutingWaypoint(null, "Điểm dẫn đường", point.getLatitude(), point.getLongitude(),
                                waypoints.size() + 1, 0));
                        destinationStopSequences.add(stop.getSequenceNumber());
                    });

            StationEntity station = stop.getStation();
            waypoints.add(new RoutingWaypoint(station.getId(), station.getName(), station.getLatitude(), station.getLongitude(),
                    waypoints.size() + 1, stop.getDwellDurationSeconds()));
            destinationStopSequences.add(stop.getSequenceNumber());
        }
        if (waypoints.size() > 50) {
            throw new RouteOperationException(HttpStatus.BAD_REQUEST, RouteErrorCode.ROUTE_VALIDATION_FAILED,
                    "Tổng trạm và điểm dẫn đường tối đa 50");
        }

        CalculatedRoute calculated = routingProvider.calculate(waypoints);
        if (calculated.sections().isEmpty()) {
            throw new RouteOperationException(HttpStatus.BAD_GATEWAY, RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE,
                    "Routing provider returned an empty route without sections");
        }
        long distance = calculated.sections().stream().mapToLong(CalculatedSection::distanceMeters).sum();
        long travel = calculated.sections().stream().mapToLong(CalculatedSection::travelDurationSeconds).sum();
        long base = calculated.sections().stream().mapToLong(CalculatedSection::baseTravelDurationSeconds).sum();
        long dwell = sourceStops.subList(1, sourceStops.size() - 1).stream()
                .mapToLong(stop -> stop.getDwellDurationSeconds()).sum();

        RouteEntity replacement = new RouteEntity(source.getName(), source.getTransportMode(), source.getRoutingProvider(),
                distance, travel, base, dwell, travel + dwell, calculated.estimatedDepartureAt(), Instant.now());
        for (RouteStopEntity stop : sourceStops) {
            StationEntity station = stop.getStation();
            replacement.addStop(new RouteStopEntity(station, stop.getSequenceNumber(), station.getName(), station.getLatitude(),
                    station.getLongitude(), stop.getDwellDurationSeconds()));
        }
        for (CalculatedSection section : calculated.sections()) {
            int localDestination = section.destinationStopSequence();
            if (localDestination < 2 || localDestination > destinationStopSequences.size()) {
                throw new RouteOperationException(HttpStatus.BAD_GATEWAY, RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE,
                        "Routing provider returned an invalid section destination");
            }
            replacement.addSection(new RouteSectionEntity(section.sectionSequence(),
                    destinationStopSequences.get(localDestination - 1), section.encodedPolyline(), section.distanceMeters(),
                    section.travelDurationSeconds(), section.baseTravelDurationSeconds()));
        }
        for (RouteShapePointEntity point : source.getShapingPoints()) {
            replacement.addShapingPoint(new RouteShapePointEntity(point.getPointOrder(), point.getDestinationStopSequence(),
                    point.getLatitude(), point.getLongitude()));
        }
        return replacement;
    }

    private RouteDetailResponse persistReplacement(RouteEntity current, RouteEntity replacement) {
        current.replaceDefinitionMetadata(replacement);
        current.clearDefinitionChildren();
        routeRepository.flush();
        current.appendDefinitionChildren(replacement);
        return RouteDetailResponse.from(routeRepository.saveAndFlush(current));
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
