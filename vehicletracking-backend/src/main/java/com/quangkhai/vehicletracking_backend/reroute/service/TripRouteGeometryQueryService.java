package com.quangkhai.vehicletracking_backend.reroute.service;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.NOT_FOUND;
@Service @RequiredArgsConstructor
public class TripRouteGeometryQueryService {
    private final TripRepository trips;
    private final TripRouteGeometryService geometry;
    @Transactional(readOnly=true) public RouteDetailResponse get(long id) {
        return geometry.route(trips.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND,"Không tìm thấy chuyến.")));
    }
}
