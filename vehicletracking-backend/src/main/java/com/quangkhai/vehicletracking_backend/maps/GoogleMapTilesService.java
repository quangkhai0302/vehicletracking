package com.quangkhai.vehicletracking_backend.maps;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;

@Service
public class GoogleMapTilesService {
    public enum Style { ROADMAP, SATELLITE, DARK }
    public record Tile(byte[] data, String contentType, String cacheControl) {}
    private record Session(String token, Instant expiresAt) {}

    private final RestClient client;
    private final GoogleMapTilesProperties properties;
    private final Clock clock;
    private final Map<Style, Session> sessions = new EnumMap<>(Style.class);

    @Autowired
    public GoogleMapTilesService(@Qualifier("googleMapTilesRestClient") RestClient client,
                                 GoogleMapTilesProperties properties, Clock operationsClock) {
        this.client = client;
        this.properties = properties;
        this.clock = operationsClock;
    }

    GoogleMapTilesService(RestClient client, GoogleMapTilesProperties properties) {
        this(client, properties, Clock.systemUTC());
    }

    public Tile tile(Style style, int z, int x, int y) {
        validate(z, x, y);
        Session session = session(style);
        URI uri = UriComponentsBuilder.fromPath("/v1/2dtiles/{z}/{x}/{y}")
                .queryParam("session", session.token()).queryParam("key", properties.getApiKey())
                .buildAndExpand(z, x, y).toUri();
        ResponseEntity<byte[]> response;
        try {
            response = client.get().uri(uri).retrieve().toEntity(byte[].class);
        } catch (ResourceAccessException ex) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Google Map Tiles phản hồi quá thời gian");
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Không thể tải tile Google");
        }
        byte[] body = response.getBody();
        if (body == null || body.length == 0) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Google Map Tiles trả về tile rỗng");
        String contentType = response.getHeaders().getContentType() == null ? "image/png"
                : response.getHeaders().getContentType().toString();
        String cacheControl = response.getHeaders().getCacheControl();
        return new Tile(body, contentType, cacheControl == null || cacheControl.isBlank() ? "private, max-age=300" : cacheControl);
    }

    private synchronized Session session(Style style) {
        if (!properties.isEnabled() || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Google Map Tiles chưa được cấu hình");
        }
        Session cached = sessions.get(style);
        Instant now = clock.instant();
        if (cached != null && cached.expiresAt().isAfter(now.plusSeconds(300))) return cached;
        URI uri = UriComponentsBuilder.fromPath("/v1/createSession").queryParam("key", properties.getApiKey()).build().toUri();
        String mapType = style == Style.SATELLITE ? "satellite" : "roadmap";
        SessionResponse response;
        try {
            response = client.post().uri(uri).body(Map.of(
                    "mapType", mapType, "language", "vi-VN", "region", "VN"
            )).retrieve().body(SessionResponse.class);
        } catch (ResourceAccessException ex) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Google Map Tiles phản hồi quá thời gian");
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Không thể tạo phiên Google Map Tiles");
        }
        if (response == null || response.session() == null || response.session().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Google Map Tiles không tạo được session");
        }
        Instant expiry = parseExpiry(response.expiry(), now);
        Session created = new Session(response.session(), expiry);
        sessions.put(style, created);
        return created;
    }

    private Instant parseExpiry(String expiry, Instant fallback) {
        if (expiry == null) return fallback.plusSeconds(3600);
        try { return Instant.ofEpochSecond(Long.parseLong(expiry)); }
        catch (NumberFormatException ignored) {
            try { return Instant.parse(expiry); }
            catch (RuntimeException invalid) { return fallback.plusSeconds(3600); }
        }
    }

    private void validate(int z, int x, int y) {
        if (z < 0 || z > 22) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zoom tile không hợp lệ");
        long limit = 1L << z;
        if (x < 0 || y < 0 || x >= limit || y >= limit) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tọa độ tile không hợp lệ");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SessionResponse(String session, String expiry) {}
}
