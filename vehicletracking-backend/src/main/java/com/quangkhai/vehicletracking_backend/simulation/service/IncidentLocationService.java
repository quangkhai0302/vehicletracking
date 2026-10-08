package com.quangkhai.vehicletracking_backend.simulation.service;

import java.math.BigDecimal;
import java.util.Comparator;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import com.quangkhai.vehicletracking_backend.station.service.StationGeocodingService;
import com.quangkhai.vehicletracking_backend.simulation.motion.RouteMotion.Frame;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStopEntity;
import com.quangkhai.vehicletracking_backend.checkin.geometry.GeofenceCrossing;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class IncidentLocationService {
    private final StationGeocodingService geocoding;

    public String locate(TripEntity trip, Frame frame) {
        try {
            var result = geocoding.reverseGeocode(BigDecimal.valueOf(frame.latitude()), BigDecimal.valueOf(frame.longitude()));
            if (result != null && result.address() != null && !result.address().isBlank())
                return limit(result.address().strip());
        } catch (ResponseStatusException | RestClientException unavailable) {
            // Reporting must remain possible when address lookup is disabled or unavailable.
        }
        var stops = trip.getStops().stream().sorted(Comparator.comparing(TripStopEntity::getSequenceNumber)).toList();
        var nearby = stops.stream().filter(s -> GeofenceCrossing.distance(frame.latitude(), frame.longitude(),
                s.getLatitude(), s.getLongitude()) <= s.getCheckinRadiusMeters())
                .min(Comparator.comparingDouble(s -> GeofenceCrossing.distance(frame.latitude(), frame.longitude(), s.getLatitude(), s.getLongitude())));
        if (nearby.isPresent()) return limit("Tại " + nearby.get().getStationName());
        var next = stops.stream().filter(s -> s.getSequenceNumber() >= frame.nextStopSequence()).findFirst();
        if (next.isPresent()) {
            var previous = stops.stream().filter(s -> s.getSequenceNumber() < next.get().getSequenceNumber()).reduce((a, b) -> b);
            return limit(previous.isPresent() ? "Trên đường từ " + previous.get().getStationName() + " đến " + next.get().getStationName()
                    : "Trên đường đến " + next.get().getStationName());
        }
        return "Chưa xác định được địa chỉ sự cố";
    }

    private String limit(String label) { return label.length() <= 500 ? label : label.substring(0, 500); }
}
