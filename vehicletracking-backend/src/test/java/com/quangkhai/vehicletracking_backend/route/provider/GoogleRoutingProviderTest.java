package com.quangkhai.vehicletracking_backend.route.provider;

import com.quangkhai.vehicletracking_backend.route.entity.PolylineEncoding;
import com.quangkhai.vehicletracking_backend.route.entity.RouteTransportMode;
import com.quangkhai.vehicletracking_backend.route.entity.TrafficSpeedCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleRoutingProviderTest {
    private MockRestServiceServer server;
    private GoogleRoutingProvider provider;

    @BeforeEach
    void setUp() {
        var properties = new GoogleRoutingProperties();
        properties.setEnabled(true);
        properties.setApiKey("fixture-google-key");
        RestClient.Builder builder = RestClient.builder().baseUrl("https://routes.googleapis.com");
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new GoogleRoutingProvider(builder.build(), properties);
    }

    @Test
    void sendsTrafficAwareRequestAndNormalizesLegsWithExplicitEncoding() {
        server.expect(requestTo("https://routes.googleapis.com/directions/v2:computeRoutes"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", "fixture-google-key"))
                .andExpect(header("X-Goog-FieldMask", GoogleRoutingProvider.FIELD_MASK))
                .andExpect(jsonPath("$.travelMode").value("TWO_WHEELER"))
                .andExpect(jsonPath("$.routingPreference").value("TRAFFIC_AWARE"))
                .andExpect(jsonPath("$.intermediates[0].via").doesNotExist())
                .andRespond(withSuccess("""
                    {"routes":[{"distanceMeters":3000,"duration":"180s","staticDuration":"150s","legs":[
                      {"distanceMeters":1000,"duration":"60s","staticDuration":"50s",
                       "polyline":{"encodedPolyline":"_p~iF~ps|U_ulLnnqC_mqNvxq`@"},
                       "travelAdvisory":{"speedReadingIntervals":[{"startPolylinePointIndex":0,"endPolylinePointIndex":2,"speed":"SLOW"}]}},
                      {"distanceMeters":2000,"duration":"120s","staticDuration":"100s",
                       "polyline":{"encodedPolyline":"_p~iF~ps|U_ulLnnqC_mqNvxq`@"}}
                    ]}]}
                    """, MediaType.APPLICATION_JSON));

        var result = provider.calculate(new RoutingRequest(waypoints(), RouteTransportMode.MOTORCYCLE,
                Instant.parse("2099-01-01T00:00:00Z"), false));

        assertThat(result.sections()).hasSize(2);
        assertThat(result.sections()).extracting(CalculatedSection::destinationStopSequence).containsExactly(2, 3);
        assertThat(result.sections()).extracting(CalculatedSection::polylineEncoding)
                .containsOnly(PolylineEncoding.GOOGLE_ENCODED_POLYLINE);
        assertThat(result.sections().getFirst().trafficIntervals()).hasSize(1);
        assertThat(result.sections().getFirst().trafficIntervals().getFirst().category()).isEqualTo(TrafficSpeedCategory.SLOW);
        server.verify();
    }

    private List<RoutingWaypoint> waypoints() {
        return List.of(
                new RoutingWaypoint(1L, "A", new BigDecimal("10.770"), new BigDecimal("106.700"), 1, 0),
                new RoutingWaypoint(2L, "B", new BigDecimal("10.780"), new BigDecimal("106.710"), 2, 60),
                new RoutingWaypoint(3L, "C", new BigDecimal("10.790"), new BigDecimal("106.720"), 3, 0));
    }
}
