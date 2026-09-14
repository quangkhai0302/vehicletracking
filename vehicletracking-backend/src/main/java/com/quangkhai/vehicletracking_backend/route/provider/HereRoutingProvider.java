package com.quangkhai.vehicletracking_backend.route.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.quangkhai.vehicletracking_backend.route.error.RouteErrorCode;
import com.quangkhai.vehicletracking_backend.route.error.RouteOperationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Component
public class HereRoutingProvider implements RoutingProvider {

    private static final Logger log = LoggerFactory.getLogger(HereRoutingProvider.class);

    private final RestClient hereRoutingRestClient;
    private final HereRoutingProperties properties;

    public HereRoutingProvider(@Qualifier("hereRoutingRestClient") RestClient hereRoutingRestClient, HereRoutingProperties properties) {
        this.hereRoutingRestClient = hereRoutingRestClient;
        this.properties = properties;
    }

    @Override
    public CalculatedRoute calculate(List<RoutingWaypoint> waypoints) {
        if (!properties.isEnabled() || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new RouteOperationException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    RouteErrorCode.ROUTING_UNAVAILABLE,
                    "Routing service is currently disabled or unconfigured"
            );
        }

        if (waypoints == null || waypoints.size() < 2) {
            throw new RouteOperationException(
                    HttpStatus.BAD_REQUEST,
                    RouteErrorCode.ROUTE_VALIDATION_FAILED,
                    "At least 2 waypoints are required to calculate a route"
            );
        }

        URI requestUri = buildUri(waypoints);

        HereRoutingResponse response;
        try {
            response = hereRoutingRestClient.get()
                    .uri(requestUri)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        HttpStatus status = HttpStatus.resolve(res.getStatusCode().value());
                        if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
                            throw new RouteOperationException(
                                    HttpStatus.SERVICE_UNAVAILABLE,
                                    RouteErrorCode.ROUTING_UNAVAILABLE,
                                    "Invalid or unauthorized routing provider credentials"
                            );
                        }
                        if (status == HttpStatus.TOO_MANY_REQUESTS || res.getStatusCode().is5xxServerError()) {
                            throw new RouteOperationException(
                                    HttpStatus.SERVICE_UNAVAILABLE,
                                    RouteErrorCode.ROUTING_PROVIDER_UNAVAILABLE,
                                    "Routing provider is currently unavailable or rate-limited"
                            );
                        }
                        if (status == HttpStatus.BAD_REQUEST) {
                            throw new RouteOperationException(
                                    HttpStatus.UNPROCESSABLE_ENTITY,
                                    RouteErrorCode.ROUTE_NOT_FOUND_BY_PROVIDER,
                                    "Routing provider rejected the request coordinates or waypoints"
                            );
                        }
                        throw new RouteOperationException(
                                HttpStatus.BAD_GATEWAY,
                                RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE,
                                "Routing provider returned error status " + res.getStatusCode()
                        );
                    })
                    .body(HereRoutingResponse.class);
        } catch (RouteOperationException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            log.warn("HERE Routing API request timed out (connect or read timeout)");
            throw new RouteOperationException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    RouteErrorCode.ROUTING_PROVIDER_TIMEOUT,
                    "Routing provider request timed out"
            );
        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED || ex.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new RouteOperationException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        RouteErrorCode.ROUTING_UNAVAILABLE,
                        "Invalid or unauthorized routing provider credentials"
                );
            }
            if (ex.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS || ex.getStatusCode().is5xxServerError()) {
                throw new RouteOperationException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        RouteErrorCode.ROUTING_PROVIDER_UNAVAILABLE,
                        "Routing provider is currently unavailable or rate-limited"
                );
            }
            throw new RouteOperationException(
                    HttpStatus.BAD_GATEWAY,
                    RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE,
                    "Routing provider error: " + ex.getStatusCode()
            );
        } catch (RestClientException ex) {
            log.warn("Failed to parse routing provider response payload");
            throw new RouteOperationException(
                    HttpStatus.BAD_GATEWAY,
                    RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE,
                    "Failed to parse routing provider response payload"
            );
        }

        return normalizeResponse(response, waypoints);
    }

    private URI buildUri(List<RoutingWaypoint> waypoints) {
        RoutingWaypoint origin = waypoints.get(0);
        RoutingWaypoint destination = waypoints.get(waypoints.size() - 1);

        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/v8/routes")
                .queryParam("transportMode", "car")
                .queryParam("routingMode", "fast")
                .queryParam("origin", origin.latitude() + "," + origin.longitude());

        for (int i = 1; i < waypoints.size() - 1; i++) {
            RoutingWaypoint wp = waypoints.get(i);
            String viaValue = wp.latitude() + "," + wp.longitude();
            if (wp.dwellDurationSeconds() > 0) {
                viaValue += "!stopDuration=" + wp.dwellDurationSeconds();
            }
            builder.queryParam("via", viaValue);
        }

        builder.queryParam("destination", destination.latitude() + "," + destination.longitude())
                .queryParam("return", "polyline,summary,travelSummary")
                .queryParam("apiKey", properties.getApiKey());

        return builder.build().toUri();
    }

    private CalculatedRoute normalizeResponse(HereRoutingResponse response, List<RoutingWaypoint> waypoints) {
        if (response == null || response.routes() == null || response.routes().isEmpty()) {
            throw new RouteOperationException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    RouteErrorCode.ROUTE_NOT_FOUND_BY_PROVIDER,
                    "No feasible route found between specified stops"
            );
        }

        HereRoute firstRoute = response.routes().get(0);
        if (firstRoute == null) {
            throw new RouteOperationException(
                    HttpStatus.BAD_GATEWAY,
                    RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE,
                    "Routing provider returned null route element"
            );
        }

        List<HereSection> sections = firstRoute.sections();
        if (sections == null || sections.isEmpty()) {
            throw new RouteOperationException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    RouteErrorCode.ROUTE_NOT_FOUND_BY_PROVIDER,
                    "Routing provider returned an empty route without sections"
            );
        }

        for (int i = 0; i < sections.size(); i++) {
            if (sections.get(i) == null) {
                throw new RouteOperationException(
                        HttpStatus.BAD_GATEWAY,
                        RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE,
                        "Routing provider returned null section element at index " + i
                );
            }
        }

        boolean hasSectionCritical = sections.stream()
                .anyMatch(sec -> hasCriticalNotice(sec.notices()));

        if (hasCriticalNotice(firstRoute.notices()) || hasCriticalNotice(response.notices()) || hasSectionCritical) {
            throw new RouteOperationException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    RouteErrorCode.ROUTE_NOT_FOUND_BY_PROVIDER,
                    "Routing provider reported a critical obstacle on the route"
            );
        }

        Instant departureTime = parseDepartureTime(sections.get(0));
        List<CalculatedSection> calculatedSections = new ArrayList<>();

        int currentDestSequence = 2;
        int nextExpectedViaIndex = 0;
        int totalViaWaypoints = waypoints.size() - 2;

        for (int i = 0; i < sections.size(); i++) {
            HereSection sec = sections.get(i);
            int sectionSeq = i + 1;

            if (sec.polyline() == null || sec.polyline().isBlank()) {
                throw new RouteOperationException(
                        HttpStatus.BAD_GATEWAY,
                        RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE,
                        "Section " + sectionSeq + " is missing polyline geometry"
                );
            }

            long distance = extractDistance(sec);
            long travelDuration = extractTravelDuration(sec);
            long baseTravelDuration = extractBaseTravelDuration(sec, travelDuration);

            calculatedSections.add(new CalculatedSection(
                    sectionSeq,
                    currentDestSequence,
                    sec.polyline(),
                    distance,
                    travelDuration,
                    baseTravelDuration
            ));

            if (sec.arrival() != null && sec.arrival().place() != null && sec.arrival().place().waypoint() != null) {
                if (i == sections.size() - 1) {
                    throw new RouteOperationException(
                            HttpStatus.BAD_GATEWAY,
                            RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE,
                            "Final section in routing provider response cannot terminate at a via waypoint"
                    );
                }
                int waypointIdx = sec.arrival().place().waypoint();
                if (waypointIdx != nextExpectedViaIndex) {
                    throw new RouteOperationException(
                            HttpStatus.BAD_GATEWAY,
                            RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE,
                            "Waypoint sequence mismatch in section " + sectionSeq + ": expected via " + nextExpectedViaIndex + " but got " + waypointIdx
                    );
                }
                nextExpectedViaIndex++;
                currentDestSequence++;
            }
        }

        if (nextExpectedViaIndex != totalViaWaypoints || currentDestSequence != waypoints.size()) {
            throw new RouteOperationException(
                    HttpStatus.BAD_GATEWAY,
                    RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE,
                    "Routing provider response missing waypoint boundaries: expected " + totalViaWaypoints + " via boundaries but found " + nextExpectedViaIndex
            );
        }

        boolean[] destStopCovered = new boolean[waypoints.size() + 1];
        for (CalculatedSection cs : calculatedSections) {
            int destSeq = cs.destinationStopSequence();
            if (destSeq >= 2 && destSeq <= waypoints.size()) {
                destStopCovered[destSeq] = true;
            }
        }
        for (int seq = 2; seq <= waypoints.size(); seq++) {
            if (!destStopCovered[seq]) {
                throw new RouteOperationException(
                        HttpStatus.BAD_GATEWAY,
                        RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE,
                        "Routing provider response missing leg to destination stop sequence " + seq
                );
            }
        }

        return new CalculatedRoute(departureTime, calculatedSections);
    }

    private boolean hasCriticalNotice(List<HereNotice> notices) {
        if (notices == null) return false;
        for (HereNotice n : notices) {
            if (n == null) {
                throw new RouteOperationException(
                        HttpStatus.BAD_GATEWAY,
                        RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE,
                        "Routing provider returned null notice element"
                );
            }
        }
        return notices.stream().anyMatch(n -> "critical".equalsIgnoreCase(n.severity()));
    }

    private Instant parseDepartureTime(HereSection firstSection) {
        if (firstSection.departure() == null || firstSection.departure().time() == null || firstSection.departure().time().isBlank()) {
            throw new RouteOperationException(
                    HttpStatus.BAD_GATEWAY,
                    RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE,
                    "Routing provider response missing valid departure time"
            );
        }
        try {
            return Instant.parse(firstSection.departure().time());
        } catch (DateTimeParseException ex) {
            throw new RouteOperationException(
                    HttpStatus.BAD_GATEWAY,
                    RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE,
                    "Routing provider response has malformed departure time: " + firstSection.departure().time()
            );
        }
    }

    private long extractDistance(HereSection sec) {
        Long val = null;
        if (sec.travelSummary() != null && sec.travelSummary().length() != null) {
            val = sec.travelSummary().length();
        } else if (sec.summary() != null && sec.summary().length() != null) {
            val = sec.summary().length();
        }
        if (val == null) {
            throw new RouteOperationException(
                    HttpStatus.BAD_GATEWAY,
                    RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE,
                    "Section is missing distance summary"
            );
        }
        if (val < 0) {
            throw new RouteOperationException(
                    HttpStatus.BAD_GATEWAY,
                    RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE,
                    "Section has negative distance: " + val
            );
        }
        return val;
    }

    private long extractTravelDuration(HereSection sec) {
        Long val = null;
        if (sec.travelSummary() != null && sec.travelSummary().duration() != null) {
            val = sec.travelSummary().duration();
        } else if (sec.summary() != null && sec.summary().duration() != null) {
            val = sec.summary().duration();
        }
        if (val == null) {
            throw new RouteOperationException(
                    HttpStatus.BAD_GATEWAY,
                    RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE,
                    "Section is missing duration summary"
            );
        }
        if (val < 0) {
            throw new RouteOperationException(
                    HttpStatus.BAD_GATEWAY,
                    RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE,
                    "Section has negative travel duration: " + val
            );
        }
        return val;
    }

    private long extractBaseTravelDuration(HereSection sec, long fallback) {
        Long val = null;
        if (sec.travelSummary() != null && sec.travelSummary().baseDuration() != null) {
            val = sec.travelSummary().baseDuration();
        } else if (sec.summary() != null && sec.summary().baseDuration() != null) {
            val = sec.summary().baseDuration();
        } else {
            val = fallback;
        }
        if (val < 0) {
            throw new RouteOperationException(
                    HttpStatus.BAD_GATEWAY,
                    RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE,
                    "Section has negative base travel duration: " + val
            );
        }
        return val;
    }

    // HERE API v8 JSON DTO mapping records
    @JsonIgnoreProperties(ignoreUnknown = true)
    record HereRoutingResponse(List<HereRoute> routes, List<HereNotice> notices) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HereRoute(String id, List<HereSection> sections, List<HereNotice> notices) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HereSection(
            String id,
            String type,
            HereEvent departure,
            HereEvent arrival,
            String polyline,
            HereSummary summary,
            HereSummary travelSummary,
            List<HereNotice> notices
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HereEvent(String time, HerePlace place) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HerePlace(String type, Integer waypoint) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HereSummary(Long duration, Long length, @JsonProperty("baseDuration") Long baseDuration) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HereNotice(String title, String code, String severity) {}
}
