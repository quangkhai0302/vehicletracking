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
    public Plan resolve(TripEntity trip) {
        var applied=revisions.findAllByTripIdOrderByRevisionNumberDesc(trip.getId()).stream()
            .filter(r -> r.getSimulationStartElapsed()!=null && Objects.equals(r.getSimulationAttemptNumber(),trip.getAttemptNumber()))
            .sorted(Comparator.comparingInt(TripRouteRevisionEntity::getRevisionNumber)).toList();
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
                .sorted(Comparator.comparingInt(TripRouteRevisionEntity::getRevisionNumber)).toList();
            for(var revision:previous) check.revise(sections(revision),revision.getSimulationStartElapsed());
            check.revise(sections(active),elapsed);
            active.applyToSimulation(elapsed,trip.getAttemptNumber());
        }
        return resolve(trip);
    }
    private List<RouteDetailResponse.RouteSectionResponse> sections(TripRouteRevisionEntity revision) {
        return revision.getSections().stream().map(s -> new RouteDetailResponse.RouteSectionResponse(s.getSectionSequence(),
            s.getDestinationStopSequence(),s.getEncodedPolyline(),s.getDistanceMeters(),s.getTravelDurationSeconds(),s.getBaseTravelDurationSeconds())).toList();
    }
}
