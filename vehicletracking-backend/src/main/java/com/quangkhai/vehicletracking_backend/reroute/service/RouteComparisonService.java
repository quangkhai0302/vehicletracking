package com.quangkhai.vehicletracking_backend.reroute.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.quangkhai.vehicletracking_backend.reroute.RerouteMessages;
import com.quangkhai.vehicletracking_backend.reroute.dto.RouteComparisonResponse;
import com.quangkhai.vehicletracking_backend.reroute.entity.RouteComparisonSnapshot;
import com.quangkhai.vehicletracking_backend.reroute.entity.TripRouteRevisionEntity;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripRouteRevisionRepository;
import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse.RouteSectionResponse;
import com.quangkhai.vehicletracking_backend.simulation.motion.FlexiblePolyline;
import com.quangkhai.vehicletracking_backend.simulation.motion.RouteMotion;

import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class RouteComparisonService {
    private final TripRouteRevisionRepository revisions;

    @Transactional(readOnly = true)
    public RouteComparisonResponse find(long tripId, long revisionId) {
        var revision = revisions.findById(revisionId).filter(r -> r.getTrip().getId().equals(tripId))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy lần đổi tuyến của chuyến này."));
        var snapshot = revision.getComparisonSnapshot();
        String status = "AVAILABLE";
        if (snapshot == null) {
            snapshot = reconstruct(revision);
            status = snapshot == null ? "UNAVAILABLE" : "RECONSTRUCTED";
        }
        if (snapshot != null) return new RouteComparisonResponse(tripId, revisionId, revision.getRevisionNumber(), revision.getCreatedAt(),
                RerouteMessages.forDisplay(revision.getReasonDetail()), status, null, snapshot.attemptNumber(), snapshot.anchor(), snapshot.before(), snapshot.after());
        RouteComparisonSnapshot.Path after = null;
        RouteComparisonSnapshot.Anchor anchor = null;
        try {
            after = RouteComparisonGeometry.path(sections(revision), revision.getRevisedRemainingSeconds());
            var point = FlexiblePolyline.decode(after.encodedPolylines().getFirst()).getFirst();
            anchor = new RouteComparisonSnapshot.Anchor(point.latitude(), point.longitude());
        } catch (IllegalArgumentException ignored) { /* Old incomplete records remain readable. */ }
        return new RouteComparisonResponse(tripId, revisionId, revision.getRevisionNumber(), revision.getCreatedAt(),
                RerouteMessages.forDisplay(revision.getReasonDetail()), status,
                "Lần đổi tuyến này chưa lưu đủ đường trước thay đổi. Chỉ hiển thị đường sau thay đổi.",
                revision.getSimulationAttemptNumber(), anchor, null, after);
    }

    private RouteComparisonSnapshot reconstruct(TripRouteRevisionEntity target) {
        if (target.getSimulationAttemptNumber() == null || target.getSimulationStartElapsed() == null) return null;
        try {
            var motion = new RouteMotion(RouteDetailResponse.from(target.getSourceRoute()));
            var previous = revisions.findAllByTripIdOrderByRevisionNumberDesc(target.getTrip().getId()).stream()
                    .filter(r -> r.getRevisionNumber() < target.getRevisionNumber() && r.getSimulationStartElapsed() != null
                            && Objects.equals(r.getSimulationAttemptNumber(), target.getSimulationAttemptNumber()))
                    .sorted(Comparator.comparingInt(TripRouteRevisionEntity::getRevisionNumber)).toList();
            for (var revision : previous) motion.revise(sections(revision), revision.getSimulationStartElapsed());
            return RouteComparisonGeometry.simulation(target.getSimulationAttemptNumber(), previous.isEmpty() ? null : previous.getLast().getId(),
                    motion, target.getSimulationStartElapsed(), sections(target),
                    Math.max(0, Math.round(motion.duration() - target.getSimulationStartElapsed())), target.getRevisedRemainingSeconds());
        } catch (IllegalArgumentException | NullPointerException ignored) { return null; }
    }

    static List<RouteSectionResponse> sections(TripRouteRevisionEntity revision) {
        return revision.getSections().stream().map(s -> new RouteSectionResponse(s.getSectionSequence(), s.getDestinationStopSequence(),
                s.getEncodedPolyline(), s.getDistanceMeters(), s.getTravelDurationSeconds(), s.getBaseTravelDurationSeconds())).toList();
    }
}
