package com.quangkhai.vehicletracking_backend.traffic.eta;

import com.quangkhai.vehicletracking_backend.traffic.TrafficSource;
import com.quangkhai.vehicletracking_backend.traffic.TrafficStatus;

import java.time.Instant;
import java.util.List;

public record TripEtaResponse(
        long tripId,
        long routeId,
        Instant calculatedAt,
        TrafficSource source,
        TrafficStatus status,
        Instant trafficObservedAt,
        Instant trafficFetchedAt,
        Integer nextStopSequence,
        long baselineRemainingSeconds,
        long totalRemainingSeconds,
        List<EtaStop> stops,
        List<AffectedSegment> affectedSegments,
        String warning,
        long geometryVersion,
        int attemptNumber,
        Long routeRevisionId
) {
    public TripEtaResponse(long tripId, long routeId, Instant calculatedAt, TrafficSource source, TrafficStatus status,
                           Instant trafficObservedAt, Instant trafficFetchedAt, Integer nextStopSequence,
                           long baselineRemainingSeconds, long totalRemainingSeconds, List<EtaStop> stops,
                           List<AffectedSegment> affectedSegments, String warning) {
        this(tripId, routeId, calculatedAt, source, status, trafficObservedAt, trafficFetchedAt, nextStopSequence,
                baselineRemainingSeconds, totalRemainingSeconds, stops, affectedSegments, warning, 1, 1, null);
    }
    /** Source compatibility for callers created before baseline ETA was exposed. */
    public TripEtaResponse(long tripId, long routeId, Instant calculatedAt, TrafficSource source, TrafficStatus status,
                           Instant trafficObservedAt, Instant trafficFetchedAt, Integer nextStopSequence,
                           long totalRemainingSeconds, List<EtaStop> stops, List<AffectedSegment> affectedSegments,
                           String warning) {
        this(tripId, routeId, calculatedAt, source, status, trafficObservedAt, trafficFetchedAt, nextStopSequence,
                totalRemainingSeconds, totalRemainingSeconds, stops, affectedSegments, warning, 1, 1, null);
    }

    public TripEtaResponse {
        stops = stops == null ? List.of() : List.copyOf(stops);
        affectedSegments = affectedSegments == null ? List.of() : List.copyOf(affectedSegments);
    }

    public record EtaStop(
            int sequenceNumber,
            String stationName,
            String state,
            Instant etaAt,
            Long etaSeconds,
            Instant actualArrivalAt,
            TrafficSource source
    ) {}

    public record AffectedSegment(
            int sectionSequence,
            int destinationStopSequence,
            String kind,
            String id,
            double jamFactor,
            String traversability,
            List<List<Double>> points,
            List<Double> center
    ) {
        public AffectedSegment(int sectionSequence, int destinationStopSequence, String kind, String id,
                               double jamFactor, String traversability) {
            this(sectionSequence, destinationStopSequence, kind, id, jamFactor, traversability, List.of(), List.of());
        }

        public AffectedSegment {
            points = points == null ? List.of() : points.stream().map(List::copyOf).toList();
            center = center == null ? List.of() : List.copyOf(center);
        }
    }
}
