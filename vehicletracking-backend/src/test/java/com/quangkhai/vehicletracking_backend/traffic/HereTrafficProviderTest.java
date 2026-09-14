package com.quangkhai.vehicletracking_backend.traffic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HereTrafficProviderTest {

    private HereTrafficPropertiesFixture fixture;
    private MockRestServiceServer mockServer;
    private HereTrafficProvider provider;

    @BeforeEach
    void setUp() {
        fixture = new HereTrafficPropertiesFixture();
        RestClient.Builder builder = RestClient.builder().baseUrl(fixture.properties.getApiBaseUrl());
        mockServer = MockRestServiceServer.bindTo(builder).build();
        provider = new HereTrafficProvider(builder.build(), fixture.properties, java.time.Clock.systemUTC());
    }

    @Test
    void fetchFlow_callsHereV7AndMapsCurrentFlow() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith("https://data.traffic.hereapi.com/v7/flow")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("in", "bbox:106.640000,10.740000,106.740000,10.840000"))
                .andExpect(queryParam("locationReferencing", "shape"))
                .andExpect(queryParam("apiKey", "test-key"))
                .andRespond(withSuccess(new ClassPathResource("fixtures/here-flow.json"), MediaType.APPLICATION_JSON));

        TrafficProvider.TrafficPayload<TrafficFlowSegment> payload = provider.fetchFlow(
                TrafficBounds.of(106.64, 10.74, 106.74, 10.84, 100));

        assertThat(payload.observedAt()).isEqualTo(java.time.Instant.parse("2026-09-09T08:00:00Z"));
        assertThat(payload.results()).singleElement().satisfies(flow -> {
            assertThat(flow.description()).isEqualTo("Nguyen Hue, District 1");
            assertThat(flow.lengthMeters()).isEqualTo(500);
            assertThat(flow.speedKmh()).isCloseTo(29.988, org.assertj.core.data.Offset.offset(0.001));
            assertThat(flow.freeFlowKmh()).isCloseTo(39.996, org.assertj.core.data.Offset.offset(0.001));
            assertThat(flow.jamFactor()).isEqualTo(4.5);
            assertThat(flow.points()).containsExactly(List.of(10.7745, 106.7025), List.of(10.7755, 106.7035));
        });
        mockServer.verify();
    }

    @Test
    void fetchIncidents_mapsDescriptionAndExpiryStatus() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith("https://data.traffic.hereapi.com/v7/incidents")))
                .andExpect(queryParam("in", "bbox:106.640000,10.740000,106.740000,10.840000"))
                .andRespond(withSuccess(new ClassPathResource("fixtures/here-incidents.json"), MediaType.APPLICATION_JSON));

        TrafficProvider.TrafficPayload<TrafficIncident> payload = provider.fetchIncidents(
                TrafficBounds.of(106.64, 10.74, 106.74, 10.84, 100));

        assertThat(payload.results()).singleElement().satisfies(incident -> {
            assertThat(incident.id()).isEqualTo("incident-hcmc-001");
            assertThat(incident.type()).isEqualTo("road_works");
            assertThat(incident.criticality()).isEqualTo("major");
            assertThat(incident.description()).isEqualTo("Road resurfacing work");
            assertThat(incident.center()).containsExactly(10.7808, 106.6993);
            assertThat(incident.status()).isEqualTo("EXPIRED");
        });
        mockServer.verify();
    }

    @Test
    void fetchFlow_whenUnauthorized_surfacesProviderErrorWithoutLeakingKey() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith("https://data.traffic.hereapi.com/v7/flow")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> provider.fetchFlow(TrafficBounds.of(106.64, 10.74, 106.74, 10.84, 100)))
                .isInstanceOf(TrafficProviderException.class)
                .satisfies(error -> {
                    TrafficProviderException exception = (TrafficProviderException) error;
                    assertThat(exception.getKind()).isEqualTo(TrafficProviderException.Kind.UNAUTHORIZED);
                    assertThat(exception.getMessage()).doesNotContain("test-key");
                });
        mockServer.verify();
    }

    @Test
    void fetchFlow_whenDisabled_doesNotCallUpstream() {
        fixture.properties.setEnabled(false);

        assertThatThrownBy(() -> provider.fetchFlow(TrafficBounds.of(106.64, 10.74, 106.74, 10.84, 100)))
                .isInstanceOf(TrafficProviderException.class)
                .extracting(error -> ((TrafficProviderException) error).getKind())
                .isEqualTo(TrafficProviderException.Kind.UNAVAILABLE);
    }

    @Test
    void fetchFlow_whenBaseUrlIsInvalid_returnsControlledProviderError() {
        fixture.properties.setBaseUrl("://invalid-url");

        assertThatThrownBy(() -> provider.fetchFlow(TrafficBounds.of(106.64, 10.74, 106.74, 10.84, 100)))
                .isInstanceOf(TrafficProviderException.class)
                .extracting(error -> ((TrafficProviderException) error).getKind())
                .isEqualTo(TrafficProviderException.Kind.INVALID_RESPONSE);
    }

    @Test
    void fetchTile_usesHereTrafficRasterTileEndpoint() {
        byte[] png = new byte[]{(byte) 137, 80, 78, 71};
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith("https://traffic.maps.hereapi.com/v3/flow/mc/12/3261/1916/png8")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("apiKey", "test-key"))
                .andRespond(withSuccess(png, MediaType.IMAGE_PNG));

        assertThat(provider.fetchTile(12, 3261, 1916)).containsExactly(png);
        mockServer.verify();
    }

    @Test
    void fetchTile_whenUpstreamFails_returnsTransparentTile() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith("https://traffic.maps.hereapi.com/v3/flow/mc/12/3261/1916/png8")))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThat(provider.fetchTile(12, 3261, 1916)).containsExactly(HereTrafficProvider.EMPTY_TILE);
        mockServer.verify();
    }

    private static final class HereTrafficPropertiesFixture {
        private final com.quangkhai.vehicletracking_backend.config.HereTrafficProperties properties = properties();

        private static com.quangkhai.vehicletracking_backend.config.HereTrafficProperties properties() {
            var properties = new com.quangkhai.vehicletracking_backend.config.HereTrafficProperties();
            properties.setEnabled(true);
            properties.setBaseUrl("https://data.traffic.hereapi.com");
            properties.setApiKey("test-key");
            return properties;
        }
    }
}
