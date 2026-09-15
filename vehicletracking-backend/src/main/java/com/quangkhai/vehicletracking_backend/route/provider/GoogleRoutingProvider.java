package com.quangkhai.vehicletracking_backend.route.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.quangkhai.vehicletracking_backend.route.entity.PolylineEncoding;
import com.quangkhai.vehicletracking_backend.route.entity.RouteTransportMode;
import com.quangkhai.vehicletracking_backend.route.entity.RouteTrafficInterval;
import com.quangkhai.vehicletracking_backend.route.entity.RoutingProviderName;
import com.quangkhai.vehicletracking_backend.route.entity.TrafficSpeedCategory;
import com.quangkhai.vehicletracking_backend.route.error.RouteErrorCode;
import com.quangkhai.vehicletracking_backend.route.error.RouteOperationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GoogleRoutingProvider implements RoutingProvider {
    static final String FIELD_MASK = String.join(",",
            "routes.distanceMeters", "routes.duration", "routes.staticDuration", "routes.polyline.encodedPolyline",
            "routes.legs.distanceMeters", "routes.legs.duration", "routes.legs.staticDuration",
            "routes.legs.polyline.encodedPolyline", "routes.legs.travelAdvisory.speedReadingIntervals",
            "routes.routeLabels", "routes.travelAdvisory.speedReadingIntervals");
    private static final int MAX_WAYPOINTS_PER_REQUEST = 27;
    private static final Logger log = LoggerFactory.getLogger(GoogleRoutingProvider.class);

    private final RestClient client;
    private final GoogleRoutingProperties properties;
    private final GoogleRoutingRequestGuard requestGuard;

    @Autowired
    public GoogleRoutingProvider(@Qualifier("googleRoutingRestClient") RestClient client,
                                 GoogleRoutingProperties properties,
                                 GoogleRoutingRequestGuard requestGuard) {
        this.client = client;
        this.properties = properties;
        this.requestGuard = requestGuard;
    }

    GoogleRoutingProvider(RestClient client, GoogleRoutingProperties properties) {
        this(client, properties, new GoogleRoutingRequestGuard(properties, java.time.Clock.systemUTC()));
    }

    @Override
    public RoutingProviderName name() {
        return RoutingProviderName.GOOGLE;
    }

    @Override
    public CalculatedRoute calculate(RoutingRequest request) {
        validate(request);
        Instant departure = request.departureTime().isBefore(Instant.now()) ? Instant.now() : request.departureTime();
        List<CalculatedSection> sections = new ArrayList<>();
        int start = 0;
        while (start < request.waypoints().size() - 1) {
            int end = Math.min(start + MAX_WAYPOINTS_PER_REQUEST - 1, request.waypoints().size() - 1);
            if (end < request.waypoints().size() - 1) {
                while (end > start && request.waypoints().get(end).stationId() == null) end--;
                if (end == start) {
                    throw invalid("Too many shaping points between two required stops");
                }
            }
            List<RoutingWaypoint> batch = request.waypoints().subList(start, end + 1);
            List<CalculatedSection> batchSections = calculateBatch(batch, start, request.transportMode(), departure,
                    request.alternatives() && start == 0 && end == request.waypoints().size() - 1);
            for (CalculatedSection section : batchSections) {
                sections.add(new CalculatedSection(sections.size() + 1, section.destinationStopSequence(),
                        section.encodedPolyline(), section.polylineEncoding(), section.distanceMeters(),
                        section.travelDurationSeconds(), section.baseTravelDurationSeconds(), section.trafficIntervals()));
            }
            long travel = batchSections.stream().mapToLong(CalculatedSection::travelDurationSeconds).sum();
            long dwell = batch.stream().skip(1).mapToLong(RoutingWaypoint::dwellDurationSeconds).sum();
            departure = departure.plusSeconds(travel + dwell);
            start = end;
        }
        return new CalculatedRoute(request.departureTime(), List.copyOf(sections));
    }

    private List<CalculatedSection> calculateBatch(List<RoutingWaypoint> waypoints, int globalStart,
                                                    RouteTransportMode mode, Instant departure,
                                                    boolean alternatives) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("origin", waypoint(waypoints.getFirst(), false));
        body.put("destination", waypoint(waypoints.getLast(), false));
        if (waypoints.size() > 2) {
            List<Map<String, Object>> intermediates = new ArrayList<>();
            for (int i = 1; i < waypoints.size() - 1; i++) {
                RoutingWaypoint point = waypoints.get(i);
                intermediates.add(waypoint(point, point.stationId() == null));
            }
            body.put("intermediates", intermediates);
        }
        body.put("travelMode", mode == RouteTransportMode.MOTORCYCLE ? "TWO_WHEELER" : "DRIVE");
        body.put("routingPreference", "TRAFFIC_AWARE");
        body.put("departureTime", departure.toString());
        boolean hasStopoverIntermediate = waypoints.subList(1, waypoints.size() - 1).stream()
                .anyMatch(point -> point.stationId() != null);
        body.put("computeAlternativeRoutes", alternatives && !hasStopoverIntermediate);
        body.put("polylineQuality", "HIGH_QUALITY");
        body.put("polylineEncoding", "ENCODED_POLYLINE");
        body.put("extraComputations", List.of("TRAFFIC_ON_POLYLINE"));

        GoogleResponse response;
        try {
            response = requestGuard.execute(() -> client.post().uri("/directions/v2:computeRoutes")
                    .header("X-Goog-Api-Key", properties.getApiKey())
                    .header("X-Goog-FieldMask", FIELD_MASK)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        HttpStatus status = HttpStatus.resolve(res.getStatusCode().value());
                        if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
                            throw unavailable("Invalid or unauthorized Google Routes credentials");
                        }
                        if (status == HttpStatus.TOO_MANY_REQUESTS || res.getStatusCode().is5xxServerError()) {
                            throw unavailable("Google Routes is unavailable or rate-limited");
                        }
                        if (status == HttpStatus.BAD_REQUEST) {
                            throw noRoute("Google Routes rejected the route request");
                        }
                        throw invalid("Google Routes returned HTTP " + res.getStatusCode());
                    }).body(GoogleResponse.class));
        } catch (RouteOperationException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            throw new RouteOperationException(HttpStatus.GATEWAY_TIMEOUT, RouteErrorCode.ROUTING_PROVIDER_TIMEOUT,
                    "Google Routes request timed out");
        } catch (RestClientException ex) {
            log.warn("Failed to call or parse Google Routes response");
            throw invalid("Google Routes returned an invalid response");
        }
        if (response == null || response.routes() == null || response.routes().isEmpty()) {
            throw noRoute("Google Routes did not find a feasible route");
        }
        GoogleRoute route = response.routes().stream().filter(java.util.Objects::nonNull)
                .min(java.util.Comparator.comparingLong(this::routeDuration)).orElse(null);
        if (route == null || route.legs() == null || route.legs().isEmpty()) {
            throw invalid("Google Routes response is missing route legs");
        }
        List<Integer> destinationIndexes = new ArrayList<>();
        for (int i = 1; i < waypoints.size(); i++) {
            if (waypoints.get(i).stationId() != null || i == waypoints.size() - 1) destinationIndexes.add(i);
        }
        if (route.legs().size() != destinationIndexes.size()) {
            throw invalid("Google Routes returned " + route.legs().size() + " legs for "
                    + destinationIndexes.size() + " required destinations");
        }
        List<CalculatedSection> result = new ArrayList<>();
        for (int i = 0; i < route.legs().size(); i++) {
            GoogleLeg leg = route.legs().get(i);
            if (leg == null || leg.polyline() == null || leg.polyline().encodedPolyline() == null
                    || leg.polyline().encodedPolyline().isBlank()) {
                throw invalid("Google Routes leg " + (i + 1) + " is missing polyline geometry");
            }
            long duration = seconds(leg.duration(), "duration");
            long staticDuration = leg.staticDuration() == null ? duration : seconds(leg.staticDuration(), "staticDuration");
            if (leg.distanceMeters() == null || leg.distanceMeters() < 0) {
                throw invalid("Google Routes leg " + (i + 1) + " has invalid distance");
            }
            int globalDestination = globalStart + destinationIndexes.get(i) + 1;
            result.add(new CalculatedSection(i + 1, globalDestination, leg.polyline().encodedPolyline(),
                    PolylineEncoding.GOOGLE_ENCODED_POLYLINE, leg.distanceMeters(), duration, staticDuration,
                    intervals(leg)));
        }
        return result;
    }

    private long routeDuration(GoogleRoute route) {
        if (route.duration() != null) {
            try { return seconds(route.duration(), "duration"); }
            catch (RouteOperationException ignored) { /* Sum valid legs below. */ }
        }
        if (route.legs() == null) return Long.MAX_VALUE;
        long result = 0;
        for (GoogleLeg leg : route.legs()) {
            if (leg == null) return Long.MAX_VALUE;
            try { result = Math.addExact(result, seconds(leg.duration(), "duration")); }
            catch (RuntimeException invalid) { return Long.MAX_VALUE; }
        }
        return result;
    }

    private List<RouteTrafficInterval> intervals(GoogleLeg leg) {
        if (leg.travelAdvisory() == null || leg.travelAdvisory().speedReadingIntervals() == null) return List.of();
        int pointCount;
        try {
            pointCount = com.quangkhai.vehicletracking_backend.simulation.motion.GooglePolyline
                    .decode(leg.polyline().encodedPolyline()).size();
        } catch (RuntimeException ex) {
            return List.of();
        }
        List<RouteTrafficInterval> result = new ArrayList<>();
        int previousEnd = 0;
        for (GoogleSpeedInterval interval : leg.travelAdvisory().speedReadingIntervals()) {
            if (interval == null || interval.endPolylinePointIndex() == null) continue;
            int start = interval.startPolylinePointIndex() == null ? previousEnd : interval.startPolylinePointIndex();
            int end = interval.endPolylinePointIndex();
            if (start < 0 || end <= start || end > pointCount - 1) continue;
            TrafficSpeedCategory category;
            try {
                category = TrafficSpeedCategory.valueOf(interval.speed() == null ? "UNKNOWN" : interval.speed());
            } catch (IllegalArgumentException ex) {
                category = TrafficSpeedCategory.UNKNOWN;
            }
            result.add(new RouteTrafficInterval(start, end, category));
            previousEnd = end;
        }
        return List.copyOf(result);
    }

    private Map<String, Object> waypoint(RoutingWaypoint waypoint, boolean via) {
        Map<String, Object> latLng = Map.of("latitude", waypoint.latitude(), "longitude", waypoint.longitude());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("location", Map.of("latLng", latLng));
        if (via) result.put("via", true);
        return result;
    }

    private void validate(RoutingRequest request) {
        if (!properties.isEnabled() || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw unavailable("Google Routes is disabled or unconfigured");
        }
        if (request.waypoints().size() < 2) throw invalid("At least 2 waypoints are required");
        if (request.waypoints().stream().anyMatch(point -> point == null || point.latitude() == null || point.longitude() == null)) {
            throw invalid("Route waypoints must have valid coordinates");
        }
    }

    private long seconds(String value, String field) {
        if (value == null || !value.endsWith("s")) throw invalid("Google Routes leg is missing " + field);
        try {
            double parsed = Double.parseDouble(value.substring(0, value.length() - 1));
            if (!Double.isFinite(parsed) || parsed < 0) throw new NumberFormatException();
            return Math.round(parsed);
        } catch (NumberFormatException ex) {
            throw invalid("Google Routes leg has invalid " + field);
        }
    }

    private RouteOperationException invalid(String detail) {
        return new RouteOperationException(HttpStatus.BAD_GATEWAY, RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE, detail);
    }
    private RouteOperationException noRoute(String detail) {
        return new RouteOperationException(HttpStatus.UNPROCESSABLE_ENTITY, RouteErrorCode.ROUTE_NOT_FOUND_BY_PROVIDER, detail);
    }
    private RouteOperationException unavailable(String detail) {
        return new RouteOperationException(HttpStatus.SERVICE_UNAVAILABLE, RouteErrorCode.ROUTING_UNAVAILABLE, detail);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GoogleResponse(List<GoogleRoute> routes) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GoogleRoute(Integer distanceMeters, String duration, String staticDuration, GooglePolyline polyline,
                       List<GoogleLeg> legs) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GoogleLeg(Integer distanceMeters, String duration, String staticDuration, GooglePolyline polyline,
                     GoogleTravelAdvisory travelAdvisory) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GooglePolyline(String encodedPolyline) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GoogleTravelAdvisory(List<GoogleSpeedInterval> speedReadingIntervals) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GoogleSpeedInterval(Integer startPolylinePointIndex, Integer endPolylinePointIndex, String speed) {}
}
