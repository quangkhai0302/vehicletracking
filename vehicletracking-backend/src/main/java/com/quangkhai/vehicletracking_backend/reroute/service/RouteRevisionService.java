package com.quangkhai.vehicletracking_backend.reroute.service;

import com.quangkhai.vehicletracking_backend.reroute.dto.RouteRevisionResponse;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripRouteRevisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.quangkhai.vehicletracking_backend.reroute.entity.RouteRevisionStatus;

@Service
@RequiredArgsConstructor
public class RouteRevisionService {
    private final TripRouteRevisionRepository revisions;
    private final Clock operationsClock;
    @Transactional(readOnly = true)
    public List<RouteRevisionResponse> findByTrip(long tripId) {
        return revisions.findAllByTripIdOrderByRevisionNumberDesc(tripId).stream().map(RouteRevisionResponse::from).toList();
    }
    @Transactional
    public RouteRevisionResponse supersede(long tripId, long revisionId) {
        var revision = revisions.findById(revisionId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên bản tuyến."));
        if (!revision.getTrip().getId().equals(tripId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên bản tuyến của chuyến này.");
        if (revision.getStatus() == RouteRevisionStatus.ACTIVE) revision.supersede(operationsClock.instant());
        return RouteRevisionResponse.from(revision);
    }
}
