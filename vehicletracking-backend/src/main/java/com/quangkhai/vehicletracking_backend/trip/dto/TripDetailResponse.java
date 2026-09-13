package com.quangkhai.vehicletracking_backend.trip.dto;

import com.quangkhai.vehicletracking_backend.trip.entity.*;
import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TripDetailResponse(TripSummaryResponse trip, List<Stop> stops, RouteDetailResponse route) {
    public record Stop(int sequenceNumber, Long stationId, String stationName, BigDecimal latitude,
            BigDecimal longitude, int checkinRadiusMeters, int dwellDurationSeconds,
            long arrivalOffsetSeconds, long departureOffsetSeconds,
            Instant plannedArrivalAt, Instant plannedDepartureAt) {
        static Stop from(TripStopEntity stop) {
            return new Stop(stop.getSequenceNumber(), stop.getStationId(), stop.getStationName(),
                    stop.getLatitude(), stop.getLongitude(), stop.getCheckinRadiusMeters(), stop.getDwellDurationSeconds(),
                    stop.getArrivalOffsetSeconds(), stop.getDepartureOffsetSeconds(),
                    stop.getPlannedArrivalAt(), stop.getPlannedDepartureAt());
        }
    }
    public static TripDetailResponse from(TripEntity trip) {
        return new TripDetailResponse(TripSummaryResponse.from(trip),
                trip.getStops().stream().map(Stop::from).toList(), RouteDetailResponse.from(trip.getRoute()));
    }
}
