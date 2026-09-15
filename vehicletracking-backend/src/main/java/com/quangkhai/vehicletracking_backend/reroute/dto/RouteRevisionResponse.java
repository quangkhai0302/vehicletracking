package com.quangkhai.vehicletracking_backend.reroute.dto;

import com.quangkhai.vehicletracking_backend.reroute.entity.*;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;

public record RouteRevisionResponse(long id, long tripId, long sourceRouteId, int revisionNumber,
                                    RouteRevisionStatus status, RerouteReasonCode reasonCode,
                                    String reasonDetail, String triggerIncidentId, NotificationSeverity severity,
                                    long baselineRemainingSeconds, long revisedRemainingSeconds,
                                    Instant createdAt, Instant activatedAt, Instant supersededAt,
                                    List<RevisionStop> stops, List<RevisionSection> sections) {
    public static RouteRevisionResponse from(TripRouteRevisionEntity item) {
        return new RouteRevisionResponse(item.getId(), item.getTrip().getId(), item.getSourceRoute().getId(),
                item.getRevisionNumber(), item.getStatus(), item.getReasonCode(), item.getReasonDetail(),
                item.getTriggerIncidentId(), item.getSeverity(), item.getBaselineRemainingSeconds(),
                item.getRevisedRemainingSeconds(), item.getCreatedAt(), item.getActivatedAt(), item.getSupersededAt(),
                item.getStops().stream().map(RevisionStop::from).toList(),
                item.getSections().stream().map(RevisionSection::from).toList());
    }
    public record RevisionStop(int originalStopSequence, int sequenceNumber, long stationId, String stationName,
                               BigDecimal latitude, BigDecimal longitude, int dwellDurationSeconds,
                               Instant baselineArrivalAt, Instant baselineDepartureAt,
                               Instant revisedArrivalAt, Instant revisedDepartureAt) {
        static RevisionStop from(TripRouteRevisionStopEntity item) {
            return new RevisionStop(item.getOriginalStopSequence(), item.getSequenceNumber(), item.getStationId(),
                    item.getStationName(), item.getLatitude(), item.getLongitude(), item.getDwellDurationSeconds(),
                    item.getBaselineArrivalAt(), item.getBaselineDepartureAt(), item.getRevisedArrivalAt(), item.getRevisedDepartureAt());
        }
    }
    public record RevisionSection(int sectionSequence, int destinationStopSequence, String encodedPolyline,
                                  com.quangkhai.vehicletracking_backend.route.entity.PolylineEncoding polylineEncoding,
                                  long distanceMeters, long travelDurationSeconds, long baseTravelDurationSeconds,
                                  List<com.quangkhai.vehicletracking_backend.route.entity.RouteTrafficInterval> trafficIntervals) {
        static RevisionSection from(TripRouteRevisionSectionEntity item) {
            return new RevisionSection(item.getSectionSequence(), item.getDestinationStopSequence(), item.getEncodedPolyline(),
                    item.getPolylineEncoding(),
                    item.getDistanceMeters(), item.getTravelDurationSeconds(), item.getBaseTravelDurationSeconds(),
                    item.getTrafficIntervals());
        }
    }
}
