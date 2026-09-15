package com.quangkhai.vehicletracking_backend.route.dto;

import com.quangkhai.vehicletracking_backend.route.entity.RouteEntity;
import com.quangkhai.vehicletracking_backend.route.entity.RouteSectionEntity;
import com.quangkhai.vehicletracking_backend.route.entity.RouteStopEntity;
import com.quangkhai.vehicletracking_backend.route.entity.RouteTransportMode;
import com.quangkhai.vehicletracking_backend.route.entity.RoutingProviderName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record RouteDetailResponse(
        Long id,
        String name,
        RouteTransportMode transportMode,
        RoutingProviderName routingProvider,
        long totalDistanceMeters,
        long estimatedTravelDurationSeconds,
        long baseTravelDurationSeconds,
        long totalDwellDurationSeconds,
        long estimatedTripDurationSeconds,
        Instant estimatedDepartureAt,
        Instant calculatedAt,
        Instant createdAt,
        long geometryVersion,
        Instant providerContentExpiresAt,
        List<RouteStopResponse> stops,
        List<RouteSectionResponse> sections,
        List<RouteShapeRequest.ShapePoint> shapingPoints
) {
    public RouteDetailResponse(Long id,String name,RouteTransportMode mode,RoutingProviderName provider,
            long distance,long travel,long base,long dwell,long total,Instant departure,Instant calculated,Instant created,
            List<RouteStopResponse> stops,List<RouteSectionResponse> sections) {
        this(id,name,mode,provider,distance,travel,base,dwell,total,departure,calculated,created,1,null,stops,sections,List.of());
    }
    public RouteDetailResponse(Long id,String name,RouteTransportMode mode,RoutingProviderName provider,
            long distance,long travel,long base,long dwell,long total,Instant departure,Instant calculated,Instant created,
            List<RouteStopResponse> stops,List<RouteSectionResponse> sections,List<RouteShapeRequest.ShapePoint> shapingPoints) {
        this(id,name,mode,provider,distance,travel,base,dwell,total,departure,calculated,created,1,null,stops,sections,shapingPoints);
    }
    public record RouteStopResponse(
            int sequenceNumber,
            String role,
            Long stationId,
            String stationName,
            BigDecimal latitude,
            BigDecimal longitude,
            int dwellDurationSeconds,
            long distanceFromPreviousMeters,
            long travelDurationFromPreviousSeconds,
            long arrivalOffsetSeconds,
            long departureOffsetSeconds
    ) {}

    public record RouteSectionResponse(
            int sectionSequence,
            int destinationStopSequence,
            String encodedPolyline,
            com.quangkhai.vehicletracking_backend.route.entity.PolylineEncoding polylineEncoding,
            long distanceMeters,
            long travelDurationSeconds,
            long baseTravelDurationSeconds,
            List<com.quangkhai.vehicletracking_backend.route.entity.RouteTrafficInterval> trafficIntervals
    ) {
        public RouteSectionResponse(int sectionSequence, int destinationStopSequence, String encodedPolyline,
                                    long distanceMeters, long travelDurationSeconds, long baseTravelDurationSeconds) {
            this(sectionSequence, destinationStopSequence, encodedPolyline,
                    com.quangkhai.vehicletracking_backend.route.entity.PolylineEncoding.HERE_FLEXIBLE_POLYLINE,
                    distanceMeters, travelDurationSeconds, baseTravelDurationSeconds, List.of());
        }
        public RouteSectionResponse(int sectionSequence, int destinationStopSequence, String encodedPolyline,
                                    com.quangkhai.vehicletracking_backend.route.entity.PolylineEncoding polylineEncoding,
                                    long distanceMeters, long travelDurationSeconds, long baseTravelDurationSeconds) {
            this(sectionSequence, destinationStopSequence, encodedPolyline, polylineEncoding,
                    distanceMeters, travelDurationSeconds, baseTravelDurationSeconds, List.of());
        }
    }

    public static RouteDetailResponse from(RouteEntity entity) {
        List<RouteSectionEntity> rawSections = entity.getSections();
        Map<Integer, List<RouteSectionEntity>> sectionsByDestStop = rawSections.stream()
                .collect(Collectors.groupingBy(RouteSectionEntity::getDestinationStopSequence));

        List<RouteStopEntity> rawStops = entity.getStops();
        int totalStops = rawStops.size();
        List<RouteStopResponse> stopResponses = new ArrayList<>(totalStops);

        long cumulativeDepartureOffset = 0;

        for (int i = 0; i < totalStops; i++) {
            RouteStopEntity stop = rawStops.get(i);
            int seq = stop.getSequenceNumber();

            String role;
            if (i == 0) {
                role = "START";
            } else if (i == totalStops - 1) {
                role = "END";
            } else {
                role = "STOP";
            }

            long distFromPrev = 0;
            long travelFromPrev = 0;
            long arrivalOffset;
            long departureOffset;

            if (i == 0) {
                arrivalOffset = 0;
                departureOffset = 0;
                cumulativeDepartureOffset = 0;
            } else {
                List<RouteSectionEntity> legSections = sectionsByDestStop.getOrDefault(seq, List.of());
                distFromPrev = legSections.stream().mapToLong(RouteSectionEntity::getDistanceMeters).sum();
                travelFromPrev = legSections.stream().mapToLong(RouteSectionEntity::getTravelDurationSeconds).sum();

                arrivalOffset = cumulativeDepartureOffset + travelFromPrev;
                departureOffset = arrivalOffset + stop.getDwellDurationSeconds();
                cumulativeDepartureOffset = departureOffset;
            }

            stopResponses.add(new RouteStopResponse(
                    seq,
                    role,
                    stop.getStation().getId(),
                    stop.getStationNameSnapshot(),
                    stop.getLatitudeSnapshot(),
                    stop.getLongitudeSnapshot(),
                    stop.getDwellDurationSeconds(),
                    distFromPrev,
                    travelFromPrev,
                    arrivalOffset,
                    departureOffset
            ));
        }

        List<RouteSectionResponse> sectionResponses = rawSections.stream()
                .map(s -> new RouteSectionResponse(
                        s.getSectionSequence(),
                        s.getDestinationStopSequence(),
                        s.getEncodedPolyline(),
                        s.getPolylineEncoding(),
                        s.getDistanceMeters(),
                        s.getTravelDurationSeconds(),
                        s.getBaseTravelDurationSeconds(),
                        s.getTrafficIntervals()
                ))
                .toList();

        return new RouteDetailResponse(
                entity.getId(),
                entity.getName(),
                entity.getTransportMode(),
                entity.getRoutingProvider(),
                entity.getTotalDistanceMeters(),
                entity.getEstimatedTravelDurationSeconds(),
                entity.getBaseTravelDurationSeconds(),
                entity.getTotalDwellDurationSeconds(),
                entity.getEstimatedTripDurationSeconds(),
                entity.getEstimatedDepartureAt(),
                entity.getCalculatedAt(),
                entity.getCreatedAt(),
                entity.getGeometryVersion(),
                entity.getProviderContentExpiresAt(),
                stopResponses,
                sectionResponses,
                entity.getShapingPoints().stream().map(p -> new RouteShapeRequest.ShapePoint(
                    p.getDestinationStopSequence(),p.getLatitude(),p.getLongitude())).toList()
        );
    }
}
