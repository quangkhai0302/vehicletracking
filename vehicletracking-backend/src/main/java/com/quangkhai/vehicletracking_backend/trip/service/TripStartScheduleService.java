package com.quangkhai.vehicletracking_backend.trip.service;

import com.quangkhai.vehicletracking_backend.traffic.TrafficSource;
import com.quangkhai.vehicletracking_backend.traffic.TrafficStatus;
import com.quangkhai.vehicletracking_backend.traffic.eta.TrafficEtaService;
import com.quangkhai.vehicletracking_backend.traffic.eta.TripEtaResponse;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Refreshes one trip's schedule from HERE after its start is committed. */
@Service
@RequiredArgsConstructor
public class TripStartScheduleService {
    private final TripRepository trips;
    private final TrafficEtaService trafficEta;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refresh(long tripId) {
        TripEntity trip = trips.findById(tripId).orElse(null);
        if (trip == null || trip.getStatus() != TripStatus.IN_PROGRESS) return;

        TripEtaResponse eta = trafficEta.calculateAtStart(tripId);
        if (!canApply(eta)) return;

        Set<Integer> checkedIn = eta.stops().stream()
                .filter(stop -> "CHECKED_IN".equals(stop.state()))
                .map(TripEtaResponse.EtaStop::sequenceNumber)
                .collect(java.util.stream.Collectors.toSet());
        Map<Integer, Instant> etaBySequence = new HashMap<>();
        eta.stops().stream()
                .filter(stop -> stop.etaAt() != null && !"CHECKED_IN".equals(stop.state()))
                .forEach(stop -> etaBySequence.putIfAbsent(stop.sequenceNumber(), stop.etaAt()));

        // The first stop has no incoming route section, so ETA calculation
        // quite correctly omits it. At start its refreshed arrival is now.
        trip.getStops().stream().findFirst()
                .filter(stop -> !checkedIn.contains(stop.getSequenceNumber()))
                .ifPresent(stop -> etaBySequence.putIfAbsent(stop.getSequenceNumber(), eta.calculatedAt()));
        long remainingStops = trip.getStops().stream()
                .filter(stop -> !checkedIn.contains(stop.getSequenceNumber())
                        && !etaBySequence.containsKey(stop.getSequenceNumber()))
                .count();
        // A blocked route or a partial response must not leave a mixed old/new
        // schedule in the trip detail. Keep the saved schedule as fallback.
        if (etaBySequence.isEmpty() || remainingStops > 0) return;

        trip.getStops().stream()
                .filter(stop -> !checkedIn.contains(stop.getSequenceNumber()))
                .forEach(stop -> stop.applyLiveArrival(etaBySequence.get(stop.getSequenceNumber())));
        trips.flush();
    }

    private boolean canApply(TripEtaResponse eta) {
        if (eta == null || eta.status() == TrafficStatus.BLOCKED || eta.status() == TrafficStatus.UNAVAILABLE)
            return false;
        return eta.source() == TrafficSource.HERE_LIVE || eta.source() == TrafficSource.HERE_LAST_KNOWN;
    }
}
