package com.quangkhai.vehicletracking_backend.checkin.service;

import com.quangkhai.vehicletracking_backend.checkin.dto.*;
import com.quangkhai.vehicletracking_backend.checkin.entity.TripCheckInStateEntity;
import com.quangkhai.vehicletracking_backend.checkin.repository.*;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service @RequiredArgsConstructor
public class CheckInQueryService {
    private final TripRepository trips;
    private final TripCheckInStateRepository states;
    private final TripStopVisitRepository visits;

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public TripCheckInsResponse find(long tripId) {
        var trip=trips.findById(tripId).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Không tìm thấy chuyến đi."));
        var state=states.findById(tripId).orElse(null);
        var rows=visits.findAllByTripIdOrderByStopSequenceAsc(tripId).stream().map(StopVisitResponse::from).toList();
        return new TripCheckInsResponse(trip.getId(),state==null?0:state.getRevision(),state==null?Integer.valueOf(1):state.getNextStopSequence(),state!=null&&state.isAwaitingExit(),rows);
    }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public List<TripCheckInsResponse> findAll(List<Long> tripIds) {
        if (tripIds.isEmpty()) return List.of();
        var stateByTrip=states.findAllByTripIdIn(tripIds).stream().collect(Collectors.toMap(TripCheckInStateEntity::getTripId,Function.identity()));
        var visitsByTrip=visits.findAllByTripIdInOrderByTripIdAscStopSequenceAsc(tripIds).stream().collect(Collectors.groupingBy(v->v.getTrip().getId()));
        return trips.findAllById(tripIds).stream().map(trip -> {
            var state=stateByTrip.get(trip.getId());
            return new TripCheckInsResponse(trip.getId(),state==null?0:state.getRevision(),state==null?Integer.valueOf(1):state.getNextStopSequence(),
                state!=null&&state.isAwaitingExit(),visitsByTrip.getOrDefault(trip.getId(),List.of()).stream().map(StopVisitResponse::from).toList());
        }).toList();
    }
}
