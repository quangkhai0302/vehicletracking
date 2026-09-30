package com.quangkhai.vehicletracking_backend.reroute.service;

import com.quangkhai.vehicletracking_backend.reroute.entity.*;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripRouteRevisionRepository;
import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
import com.quangkhai.vehicletracking_backend.simulation.motion.RouteMotion;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

/** Shared geometry for simulator, ETA and route-trace check-in. Caller owns the transaction. */
@Service @RequiredArgsConstructor
public class TripRouteGeometryService {
    private final TripRouteRevisionRepository revisions;
    public record Plan(RouteMotion motion,RouteDetailResponse route,Long revisionId) {}
    private final Map<String,Plan> cache=Collections.synchronizedMap(new LinkedHashMap<>(16,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,Plan> entry) { return size()>100; }
    });
    /** Original route reads must not require geometry that can be simulated. */
    public RouteDetailResponse route(TripEntity trip) {
        boolean applied=revisions.findAllByTripIdOrderByRevisionNumberDesc(trip.getId()).stream()
            .anyMatch(r -> r.getSimulationStartElapsed()!=null && Objects.equals(r.getSimulationAttemptNumber(),trip.getAttemptNumber()));
        return applied?resolve(trip).route():RouteDetailResponse.from(trip.getRoute());
    }

    /**
     * Returns the route currently assigned to an operational trip. Simulation
     * revisions are reconstructed by {@link #route(TripEntity)}; a persisted
     * live reroute has its own immutable sections and must be used directly by
     * telemetry-based detectors and tracking views.
     */
    public RouteDetailResponse routeForTracking(TripEntity trip) {
        RouteDetailResponse simulationRoute = route(trip);
        boolean hasSimulationRevision = revisions.findAllByTripIdOrderByRevisionNumberDesc(trip.getId()).stream()
                .anyMatch(r -> r.getSimulationStartElapsed() != null
                        && Objects.equals(r.getSimulationAttemptNumber(), trip.getAttemptNumber()));
        if (hasSimulationRevision) return simulationRoute;

        var active = revisions.findTopByTripIdAndStatusOrderByRevisionNumberDesc(
                trip.getId(), RouteRevisionStatus.ACTIVE).orElse(null);
        if (active == null || active.getSections().isEmpty() || active.getStops().isEmpty()) return simulationRoute;

        var source = trip.getRoute();
        var stops = active.getStops().stream().map(stop -> new RouteDetailResponse.RouteStopResponse(
                stop.getOriginalStopSequence(),
                stop.getOriginalStopSequence() == active.getStops().getFirst().getOriginalStopSequence() ? "START"
                        : stop == active.getStops().getLast() ? "END" : "STOP",
                stop.getStationId(), stop.getStationName(), stop.getLatitude(), stop.getLongitude(),
                stop.getDwellDurationSeconds(), 0, 0, 0, 0)).toList();
        var sections = active.getSections().stream().map(section -> new RouteDetailResponse.RouteSectionResponse(
                section.getSectionSequence(), section.getDestinationStopSequence(), section.getEncodedPolyline(),
                section.getDistanceMeters(), section.getTravelDurationSeconds(), section.getBaseTravelDurationSeconds(), section.getInstructions())).toList();
        long distance = sections.stream().mapToLong(section -> section.distanceMeters()).sum();
        long travel = sections.stream().mapToLong(section -> section.travelDurationSeconds()).sum();
        long baseTravel = sections.stream().mapToLong(section -> section.baseTravelDurationSeconds()).sum();
        long dwell = stops.stream().mapToLong(stop -> stop.dwellDurationSeconds()).sum();
        return new RouteDetailResponse(source.getId(), source.getName(), source.getTransportMode(), source.getRoutingProvider(),
                distance, travel, baseTravel, dwell, travel + dwell, source.getEstimatedDepartureAt(),
                source.getCalculatedAt(), source.getCreatedAt(), stops, sections, List.of());
    }
    public Plan resolve(TripEntity trip) {
        var applied=revisions.findAllByTripIdOrderByRevisionNumberDesc(trip.getId()).stream()
            .filter(r -> r.getSimulationStartElapsed()!=null && Objects.equals(r.getSimulationAttemptNumber(),trip.getAttemptNumber()))
            .sorted(Comparator.comparingInt(revision -> revision.getRevisionNumber())).toList();
        Long id=applied.isEmpty()?null:applied.getLast().getId();
        String key=trip.getId()+":"+trip.getAttemptNumber()+":"+trip.getRoute().getCalculatedAt()+":"+id
            +":"+(applied.isEmpty()?0:applied.getLast().getSimulationStartElapsed());
        return cache.computeIfAbsent(key,k -> {
            var original=RouteDetailResponse.from(trip.getRoute());var motion=new RouteMotion(original);
            for(var revision:applied) motion.revise(sections(revision),revision.getSimulationStartElapsed());
            return new Plan(motion,applied.isEmpty()?original:motion.snapshot(),id);
        });
    }
    public Plan applyActive(TripEntity trip,double elapsed) {
        if (resolve(trip).motion().at(elapsed).dwelling()) return resolve(trip);
        var active=revisions.findTopByTripIdAndStatusOrderByRevisionNumberDesc(trip.getId(),RouteRevisionStatus.ACTIVE).orElse(null);
        if (active!=null && active.getSimulationStartElapsed()==null) {
            // Validate using a fresh instance; never mutate a shared cached motion.
            var check=new RouteMotion(RouteDetailResponse.from(trip.getRoute()));
            var previous=revisions.findAllByTripIdOrderByRevisionNumberDesc(trip.getId()).stream()
                .filter(r -> r.getSimulationStartElapsed()!=null && Objects.equals(r.getSimulationAttemptNumber(),trip.getAttemptNumber()))
                .sorted(Comparator.comparingInt(revision -> revision.getRevisionNumber())).toList();
            for(var revision:previous) check.revise(sections(revision),revision.getSimulationStartElapsed());
            check.revise(sections(active),elapsed);
            active.applyToSimulation(elapsed,trip.getAttemptNumber());
        }
        return resolve(trip);
    }

    /** Check on a fresh motion instance; callers must never mutate the shared resolved plan. */
    public void validateReplacement(TripEntity trip, List<RouteDetailResponse.RouteSectionResponse> replacement, double elapsed) {
        var check = new RouteMotion(RouteDetailResponse.from(trip.getRoute()));
        revisions.findAllByTripIdOrderByRevisionNumberDesc(trip.getId()).stream()
                .filter(r -> r.getSimulationStartElapsed() != null && Objects.equals(r.getSimulationAttemptNumber(), trip.getAttemptNumber()))
                .sorted(Comparator.comparingInt(TripRouteRevisionEntity::getRevisionNumber))
                .forEach(r -> check.revise(sections(r), r.getSimulationStartElapsed()));
        check.revise(replacement, elapsed);
    }
    private List<RouteDetailResponse.RouteSectionResponse> sections(TripRouteRevisionEntity revision) {
        return revision.getSections().stream().map(s -> new RouteDetailResponse.RouteSectionResponse(s.getSectionSequence(),
            s.getDestinationStopSequence(),s.getEncodedPolyline(),s.getDistanceMeters(),s.getTravelDurationSeconds(),s.getBaseTravelDurationSeconds(),s.getInstructions())).toList();
    }
}
