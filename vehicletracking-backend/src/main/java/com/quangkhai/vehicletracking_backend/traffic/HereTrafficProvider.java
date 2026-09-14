package com.quangkhai.vehicletracking_backend.traffic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.quangkhai.vehicletracking_backend.config.HereTrafficProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class HereTrafficProvider implements TrafficProvider {
    private final RestClient client;
    private final HereTrafficProperties properties;
    private final Clock operationsClock;

    public HereTrafficProvider(@Qualifier("hereTrafficRestClient") RestClient client,
                                HereTrafficProperties properties, Clock operationsClock) {
        this.client = client;
        this.properties = properties;
        this.operationsClock = operationsClock;
    }

    @Override
    public TrafficPayload<TrafficFlowSegment> fetchFlow(TrafficBounds bounds) {
        ensureConfigured();
        HereFlowResponse response = execute("flow", bounds, HereFlowResponse.class);
        if (response == null) invalid("HERE flow response was empty");
        try {
            List<TrafficFlowSegment> results = response.results() == null ? List.of()
                    : response.results().stream().map(this::mapFlow).toList();
            return new TrafficPayload<>(parseInstant(response.sourceUpdated(), "sourceUpdated"), results);
        } catch (TrafficProviderException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new TrafficProviderException(TrafficProviderException.Kind.INVALID_RESPONSE,
                    "HERE flow response contains invalid data", ex);
        }
    }

    @Override
    public TrafficPayload<TrafficIncident> fetchIncidents(TrafficBounds bounds) {
        ensureConfigured();
        HereIncidentResponse response = execute("incidents", bounds, HereIncidentResponse.class);
        if (response == null) invalid("HERE incidents response was empty");
        try {
            List<TrafficIncident> results = response.results() == null ? List.of()
                    : response.results().stream().map(this::mapIncident).toList();
            return new TrafficPayload<>(parseInstant(response.sourceUpdated(), "sourceUpdated"), results);
        } catch (TrafficProviderException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new TrafficProviderException(TrafficProviderException.Kind.INVALID_RESPONSE,
                    "HERE incidents response contains invalid data", ex);
        }
    }

    public static final byte[] EMPTY_TILE = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
    );

    @Override
    public byte[] fetchTile(int z, int x, int y) {
        if (!properties.isEnabled() || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return EMPTY_TILE;
        }
        try {
            URI uri = UriComponentsBuilder.fromUriString(properties.getTileApiBaseUrl())
                    .path("/v3/flow/mc/" + z + "/" + x + "/" + y + "/png8")
                    .queryParam("apiKey", properties.getApiKey())
                    .build()
                    .encode()
                    .toUri();
            byte[] tile = client.get().uri(uri).retrieve().body(byte[].class);
            return tile != null && tile.length > 0 ? tile : EMPTY_TILE;
        } catch (Exception ex) {
            return EMPTY_TILE;
        }
    }

    private <T> T execute(String endpoint, TrafficBounds bounds, Class<T> type) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(properties.getApiBaseUrl())
                    .path("/" + endpoint)
                    .queryParam("in", bounds.hereBbox())
                    .queryParam("locationReferencing", "shape")
                    .queryParam("apiKey", properties.getApiKey())
                    .build()
                    .encode()
                    .toUri();
            return client.get().uri(uri).retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());
                        if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
                            throw new TrafficProviderException(TrafficProviderException.Kind.UNAUTHORIZED,
                                    "HERE Traffic credentials were rejected");
                        }
                        if (status == HttpStatus.TOO_MANY_REQUESTS || response.getStatusCode().is5xxServerError()) {
                            throw new TrafficProviderException(TrafficProviderException.Kind.UNAVAILABLE,
                                    "HERE Traffic provider is unavailable or rate-limited");
                        }
                        throw new TrafficProviderException(TrafficProviderException.Kind.INVALID_RESPONSE,
                                "HERE Traffic provider returned HTTP " + response.getStatusCode().value());
                    })
                    .body(type);
        } catch (TrafficProviderException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            throw new TrafficProviderException(TrafficProviderException.Kind.TIMEOUT,
                    "HERE Traffic provider request timed out", ex);
        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED || ex.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new TrafficProviderException(TrafficProviderException.Kind.UNAUTHORIZED,
                        "HERE Traffic credentials were rejected", ex);
            }
            if (ex.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS || ex.getStatusCode().is5xxServerError()) {
                throw new TrafficProviderException(TrafficProviderException.Kind.UNAVAILABLE,
                        "HERE Traffic provider is unavailable or rate-limited", ex);
            }
            throw new TrafficProviderException(TrafficProviderException.Kind.INVALID_RESPONSE,
                    "HERE Traffic provider returned an error", ex);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new TrafficProviderException(TrafficProviderException.Kind.INVALID_RESPONSE,
                    "HERE Traffic provider base URL is invalid", ex);
        } catch (RestClientException ex) {
            throw new TrafficProviderException(TrafficProviderException.Kind.INVALID_RESPONSE,
                    "Failed to parse HERE Traffic response", ex);
        }
    }

    private void ensureConfigured() {
        if (!properties.isEnabled() || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new TrafficProviderException(TrafficProviderException.Kind.UNAVAILABLE,
                    "Traffic service is currently disabled or unconfigured");
        }
    }

    private TrafficFlowSegment mapFlow(HereFlowItem item) {
        if (item == null || item.location() == null || item.currentFlow() == null) invalid("HERE flow item is incomplete");
        List<List<Double>> points = points(item.location());
        double length = nonNegative(item.location().length(), "flow length");
        double speed = nonNegativeOrZero(item.currentFlow().speed(), "flow speed");
        double freeFlow = nonNegativeOrZero(item.currentFlow().freeFlow(), "flow freeFlow");
        double jam = boundedJam(item.currentFlow().jamFactor());
        String description = item.location().description() == null ? "Đường không xác định" : item.location().description();
        String id = UUID.nameUUIDFromBytes((description + "|" + points).getBytes(StandardCharsets.UTF_8)).toString();
        return new TrafficFlowSegment(id, description, length, points, speed * 3.6, freeFlow * 3.6,
                jam, normalizeTraversability(item.currentFlow().traversability()), item.currentFlow().confidence());
    }

    private TrafficIncident mapIncident(HereIncidentItem item) {
        if (item == null || item.location() == null || item.incidentDetails() == null) invalid("HERE incident item is incomplete");
        List<List<Double>> points = points(item.location());
        List<Double> center = center(points);
        String id = item.id() == null || item.id().isBlank() ? UUID.randomUUID().toString() : item.id();
        String description = item.incidentDetails().description() == null
                ? "Sự cố giao thông" : item.incidentDetails().description().value();
        Instant start = parseOptionalInstant(item.incidentDetails().startTime(), "incident startTime");
        Instant end = parseOptionalInstant(item.incidentDetails().endTime(), "incident endTime");
        String status = end != null && !end.isAfter(operationsClock.instant()) ? "EXPIRED" : "ACTIVE";
        return new TrafficIncident(id, description, lower(item.incidentDetails().type()),
                lower(item.incidentDetails().criticality()), start, end, points, center, status);
    }

    private List<List<Double>> points(HereLocation location) {
        if (location.shape() == null || location.shape().links() == null) return List.of();
        List<List<Double>> result = new ArrayList<>();
        for (HereLink link : location.shape().links()) {
            if (link == null || link.points() == null) continue;
            for (HerePoint point : link.points()) {
                if (point == null || point.lat() == null || point.lng() == null
                        || !Double.isFinite(point.lat()) || !Double.isFinite(point.lng())) invalid("HERE geometry point is invalid");
                result.add(List.of(point.lat(), point.lng()));
            }
        }
        return List.copyOf(result);
    }

    private List<Double> center(List<List<Double>> points) {
        if (points.isEmpty()) return List.of();
        double lat = points.stream().mapToDouble(point -> point.get(0)).average().orElse(0);
        double lng = points.stream().mapToDouble(point -> point.get(1)).average().orElse(0);
        return List.of(lat, lng);
    }

    private Instant parseInstant(String value, String field) {
        if (value == null || value.isBlank()) return null;
        return parseOptionalInstant(value, field);
    }

    private Instant parseOptionalInstant(String value, String field) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            invalid("HERE " + field + " is malformed");
            return null;
        }
    }

    private double nonNegative(Double value, String field) {
        if (value == null || !Double.isFinite(value) || value < 0) invalid("HERE " + field + " is invalid");
        return value;
    }

    private double nonNegativeOrZero(Double value, String field) {
        if (value == null) return 0;
        return nonNegative(value, field);
    }

    private double boundedJam(Double value) {
        if (value == null || !Double.isFinite(value) || value < 0 || value > 10) invalid("HERE jamFactor is invalid");
        return value;
    }

    private String normalizeTraversability(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private String lower(String value) {
        return value == null || value.isBlank() ? "unknown" : value.toLowerCase(Locale.ROOT);
    }

    private void invalid(String message) {
        throw new TrafficProviderException(TrafficProviderException.Kind.INVALID_RESPONSE, message);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HereFlowResponse(String sourceUpdated, List<HereFlowItem> results) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HereFlowItem(HereLocation location, HereCurrentFlow currentFlow) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HereIncidentResponse(String sourceUpdated, List<HereIncidentItem> results) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HereIncidentItem(String id, HereLocation location, HereIncidentDetails incidentDetails) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HereLocation(String description, Double length, HereShape shape) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HereShape(List<HereLink> links) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HereLink(List<HerePoint> points) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HerePoint(Double lat, Double lng) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HereCurrentFlow(Double speed, Double freeFlow, Double jamFactor,
                                 String traversability, Double confidence) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HereIncidentDetails(String type, String criticality, HereDescription description,
                                      String startTime, String endTime) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HereDescription(String value) {}
}
