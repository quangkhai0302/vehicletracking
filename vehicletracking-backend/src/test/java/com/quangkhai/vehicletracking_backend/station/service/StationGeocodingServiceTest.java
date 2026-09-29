package com.quangkhai.vehicletracking_backend.station.service;

import com.quangkhai.vehicletracking_backend.station.configs.HereGeocodingProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class StationGeocodingServiceTest {
    private static final String FAKE_KEY = "fixture-key-not-real";
    private MockRestServiceServer server;
    private RestClient client;
    private StationGeocodingService service;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("https://revgeocode.search.hereapi.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = builder.build();
        service = new StationGeocodingService(client, new HereGeocodingProperties(true, FAKE_KEY, 2000, 5000));
    }

    @AfterEach
    void verifyRequests() { server.verify(); }

    @Test
    void sendsVietnameseLookupAndMapsOnlyAddressAndDistance() {
        server.expect(requestTo(startsWith("https://revgeocode.search.hereapi.com/v1/revgeocode?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("at", "10,106.700000"))
                .andExpect(queryParam("lang", "vi"))
                .andExpect(queryParam("limit", "1"))
                .andExpect(queryParam("apiKey", FAKE_KEY))
                .andRespond(withSuccess("""
                        {"items":[{"title":"unused","address":{"label":"  Địa chỉ mẫu  ","city":"HCM"},
                        "distance":12.5,"position":{"lat":11,"lng":107}}],"extra":true}
                        """, MediaType.APPLICATION_JSON));
        var result = service.reverseGeocode(new BigDecimal("1E+1"), new BigDecimal("106.700000"));
        assertThat(result.address()).isEqualTo("Địa chỉ mẫu");
        assertThat(result.distanceMeters()).isEqualTo(12.5);
    }

    @Test
    void emptyResultsReturnNullableAddress() {
        server.expect(anything()).andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));
        var result = lookup();
        assertThat(result.address()).isNull();
        assertThat(result.distanceMeters()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "{\"items\":null}", "{\"items\":[null]}",
            "{\"items\":[{}]}", "{\"items\":[{\"address\":{}}]}",
            "{\"items\":[{\"address\":{\"label\":\"  \"}}]}", "not-json"})
    void malformedOrIncompleteResultsReturnControlledBadGateway(String body) {
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertError(HttpStatus.BAD_GATEWAY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "-1", "\"NaN\"", "\"Infinity\""})
    void invalidDistanceDoesNotDiscardUsableAddress(String distance) {
        server.expect(anything()).andRespond(withSuccess(
                "{\"items\":[{\"address\":{\"label\":\"Địa chỉ\"},\"distance\":" + distance + "}]}",
                MediaType.APPLICATION_JSON));
        var result = lookup();
        assertThat(result.address()).isEqualTo("Địa chỉ");
        assertThat(result.distanceMeters()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"401,503", "403,503", "429,503", "500,503", "400,502", "502,502", "503,502"})
    void providerHttpErrorsFollowExistingContractWithoutExposingProviderBody(int upstream, int downstream) {
        server.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(upstream))
                .body("provider debug: apiKey=" + FAKE_KEY).contentType(MediaType.TEXT_PLAIN));
        assertError(HttpStatus.valueOf(downstream));
    }

    @Test
    void timeoutReturnsServiceUnavailableWithoutLeakingRequestDetails() {
        server.expect(anything()).andRespond(withException(new SocketTimeoutException("apiKey=" + FAKE_KEY)));
        assertError(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void disabledGeocodingDoesNotContactProvider() {
        service = new StationGeocodingService(client, new HereGeocodingProperties(false, FAKE_KEY, 2000, 5000));
        assertError(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void missingKeyDoesNotContactProvider() {
        service = new StationGeocodingService(client, new HereGeocodingProperties(true, null, 2000, 5000));
        assertError(HttpStatus.SERVICE_UNAVAILABLE);
        service = new StationGeocodingService(client, new HereGeocodingProperties(true, " ", 2000, 5000));
        assertError(HttpStatus.SERVICE_UNAVAILABLE);
    }

    private com.quangkhai.vehicletracking_backend.station.dto.StationAddressResponse lookup() {
        return service.reverseGeocode(new BigDecimal("10.8"), new BigDecimal("106.7"));
    }

    private void assertError(HttpStatus expected) {
        assertThatThrownBy(this::lookup).isInstanceOfSatisfying(ResponseStatusException.class, error -> {
            assertThat(error.getStatusCode()).isEqualTo(expected);
            assertThat(error.getReason()).isNotBlank().doesNotContain(FAKE_KEY, "apiKey", "provider debug");
            assertThat(error.getCause()).isNull();
        });
    }
}
