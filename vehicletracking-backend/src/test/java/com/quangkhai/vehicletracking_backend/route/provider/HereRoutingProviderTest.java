package com.quangkhai.vehicletracking_backend.route.provider;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.quangkhai.vehicletracking_backend.route.error.RouteErrorCode;
import com.quangkhai.vehicletracking_backend.route.error.RouteOperationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HereRoutingProviderTest {

    private HereRoutingProperties properties;
    private MockRestServiceServer mockServer;
    private HereRoutingProvider provider;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        properties = new HereRoutingProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("https://router.hereapi.com");
        properties.setApiKey("test-routing-key-secret-12345");
        properties.setConnectTimeoutMs(2000);
        properties.setReadTimeoutMs(10000);

        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        provider = new HereRoutingProvider(restClient, properties);

        logger = (Logger) LoggerFactory.getLogger(HereRoutingProvider.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        if (logger != null && listAppender != null) {
            logger.detachAppender(listAppender);
        }
    }

    @Test
    void calculate_whenDisabled_throwsServiceUnavailable() {
        properties.setEnabled(false);

        List<RoutingWaypoint> waypoints = sampleWaypoints();

        assertThatThrownBy(() -> provider.calculate(waypoints))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTING_UNAVAILABLE);
                });
    }

    @Test
    void calculate_whenLessThan2Waypoints_throwsBadRequest() {
        List<RoutingWaypoint> waypoints = List.of(
                new RoutingWaypoint(1L, "S1", new BigDecimal("10.8"), new BigDecimal("106.7"), 1, 0)
        );

        assertThatThrownBy(() -> provider.calculate(waypoints))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTE_VALIDATION_FAILED);
                });
    }

    @Test
    void calculate_withMultipleWaypoints_sendsExpectedQueryAndMapsSectionsCorrectly() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith("https://router.hereapi.com/v8/routes")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("transportMode", "car"))
                .andExpect(queryParam("routingMode", "fast"))
                .andExpect(queryParam("origin", "10.801234,106.710123"))
                .andExpect(queryParam("via", "10.800100,106.711100!stopDuration=120"))
                .andExpect(queryParam("destination", "10.772123,106.698123"))
                .andExpect(queryParam("return", "polyline,summary,travelSummary"))
                .andExpect(queryParam("apiKey", "test-routing-key-secret-12345"))
                .andRespond(withSuccess(new ClassPathResource("fixtures/here-route-multi-stop.json"), MediaType.APPLICATION_JSON));

        CalculatedRoute route = provider.calculate(sampleWaypoints());

        assertThat(route).isNotNull();
        assertThat(route.estimatedDepartureAt()).isEqualTo("2026-09-11T04:30:00Z");
        assertThat(route.sections()).hasSize(2);

        CalculatedSection sec1 = route.sections().get(0);
        assertThat(sec1.sectionSequence()).isEqualTo(1);
        assertThat(sec1.destinationStopSequence()).isEqualTo(2);
        assertThat(sec1.distanceMeters()).isEqualTo(3100L);
        assertThat(sec1.travelDurationSeconds()).isEqualTo(480L);
        assertThat(sec1.baseTravelDurationSeconds()).isEqualTo(410L);
        assertThat(sec1.encodedPolyline()).isEqualTo("BFoz5xJ67i1B1B7PzIhaxL7Y");

        CalculatedSection sec2 = route.sections().get(1);
        assertThat(sec2.sectionSequence()).isEqualTo(2);
        assertThat(sec2.destinationStopSequence()).isEqualTo(3);
        assertThat(sec2.distanceMeters()).isEqualTo(5100L);
        assertThat(sec2.travelDurationSeconds()).isEqualTo(780L);
        assertThat(sec2.baseTravelDurationSeconds()).isEqualTo(640L);

        mockServer.verify();
    }

    @Test
    void calculate_withTwoViaWaypoints_sendsMultipleViaInOrderAndMapsDestinations() {
        List<RoutingWaypoint> fourStops = List.of(
                new RoutingWaypoint(1L, "S1", new BigDecimal("10.801234"), new BigDecimal("106.710123"), 1, 0),
                new RoutingWaypoint(2L, "S2", new BigDecimal("10.810000"), new BigDecimal("106.720000"), 2, 60),
                new RoutingWaypoint(3L, "S3", new BigDecimal("10.820000"), new BigDecimal("106.730000"), 3, 60),
                new RoutingWaypoint(4L, "S4", new BigDecimal("10.830000"), new BigDecimal("106.740000"), 4, 0)
        );

        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith("https://router.hereapi.com/v8/routes")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("origin", "10.801234,106.710123"))
                .andExpect(queryParam("via", "10.810000,106.720000!stopDuration=60", "10.820000,106.730000!stopDuration=60"))
                .andExpect(queryParam("destination", "10.830000,106.740000"))
                .andRespond(withSuccess(new ClassPathResource("fixtures/here-route-two-vias.json"), MediaType.APPLICATION_JSON));

        CalculatedRoute route = provider.calculate(fourStops);

        assertThat(route.sections()).hasSize(3);
        assertThat(route.sections().get(0).destinationStopSequence()).isEqualTo(2);
        assertThat(route.sections().get(1).destinationStopSequence()).isEqualTo(3);
        assertThat(route.sections().get(2).destinationStopSequence()).isEqualTo(4);

        mockServer.verify();
    }

    @Test
    void calculate_whenMissingWaypointBoundary_throwsBadGateway() {
        List<RoutingWaypoint> fourStops = List.of(
                new RoutingWaypoint(1L, "S1", new BigDecimal("10.801234"), new BigDecimal("106.710123"), 1, 0),
                new RoutingWaypoint(2L, "S2", new BigDecimal("10.810000"), new BigDecimal("106.720000"), 2, 60),
                new RoutingWaypoint(3L, "S3", new BigDecimal("10.820000"), new BigDecimal("106.730000"), 3, 60),
                new RoutingWaypoint(4L, "S4", new BigDecimal("10.830000"), new BigDecimal("106.740000"), 4, 0)
        );

        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith("https://router.hereapi.com/v8/routes")))
                .andRespond(withSuccess(new ClassPathResource("fixtures/here-route-missing-waypoint-boundary.json"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.calculate(fourStops))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE);
                });

        mockServer.verify();
    }

    @Test
    void calculate_whenOutOfOrderWaypoint_throwsBadGateway() {
        List<RoutingWaypoint> fourStops = List.of(
                new RoutingWaypoint(1L, "S1", new BigDecimal("10.801234"), new BigDecimal("106.710123"), 1, 0),
                new RoutingWaypoint(2L, "S2", new BigDecimal("10.810000"), new BigDecimal("106.720000"), 2, 60),
                new RoutingWaypoint(3L, "S3", new BigDecimal("10.820000"), new BigDecimal("106.730000"), 3, 60),
                new RoutingWaypoint(4L, "S4", new BigDecimal("10.830000"), new BigDecimal("106.740000"), 4, 0)
        );

        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith("https://router.hereapi.com/v8/routes")))
                .andRespond(withSuccess(new ClassPathResource("fixtures/here-route-out-of-order-waypoint.json"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.calculate(fourStops))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE);
                });

        mockServer.verify();
    }

    @Test
    void calculate_whenSectionCriticalNotice_throwsUnprocessableEntity() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith("https://router.hereapi.com/v8/routes")))
                .andRespond(withSuccess(new ClassPathResource("fixtures/here-route-section-critical-notice.json"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.calculate(sampleWaypoints()))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTE_NOT_FOUND_BY_PROVIDER);
                });

        mockServer.verify();
    }

    @Test
    void calculate_whenMissingPolyline_throwsBadGateway() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith("https://router.hereapi.com/v8/routes")))
                .andRespond(withSuccess(new ClassPathResource("fixtures/here-route-missing-polyline.json"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.calculate(List.of(
                new RoutingWaypoint(1L, "S1", new BigDecimal("10.8"), new BigDecimal("106.7"), 1, 0),
                new RoutingWaypoint(2L, "S2", new BigDecimal("10.9"), new BigDecimal("106.8"), 2, 0)
        )))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE);
                });

        mockServer.verify();
    }

    @Test
    void calculate_whenMissingSummary_throwsBadGateway() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith("https://router.hereapi.com/v8/routes")))
                .andRespond(withSuccess(new ClassPathResource("fixtures/here-route-missing-summary.json"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.calculate(List.of(
                new RoutingWaypoint(1L, "S1", new BigDecimal("10.8"), new BigDecimal("106.7"), 1, 0),
                new RoutingWaypoint(2L, "S2", new BigDecimal("10.9"), new BigDecimal("106.8"), 2, 0)
        )))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE);
                });

        mockServer.verify();
    }

    @Test
    void calculate_whenMalformedDepartureTime_throwsRoutingProviderException() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith("https://router.hereapi.com/v8/routes")))
                .andRespond(withSuccess(new ClassPathResource("fixtures/here-route-malformed-time.json"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.calculate(List.of(
                new RoutingWaypoint(1L, "S1", new BigDecimal("10.8"), new BigDecimal("106.7"), 1, 0),
                new RoutingWaypoint(2L, "S2", new BigDecimal("10.9"), new BigDecimal("106.8"), 2, 0)
        )))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE);
                });

        mockServer.verify();
    }

    @Test
    void calculate_whenBaseDurationInSummary_extractsBaseDurationCorrectly() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith("https://router.hereapi.com/v8/routes")))
                .andRespond(withSuccess(new ClassPathResource("fixtures/here-route-base-duration-fallback.json"), MediaType.APPLICATION_JSON));

        CalculatedRoute route = provider.calculate(List.of(
                new RoutingWaypoint(1L, "S1", new BigDecimal("10.8"), new BigDecimal("106.7"), 1, 0),
                new RoutingWaypoint(2L, "S2", new BigDecimal("10.9"), new BigDecimal("106.8"), 2, 0)
        ));

        assertThat(route.sections()).hasSize(1);
        assertThat(route.sections().get(0).baseTravelDurationSeconds()).isEqualTo(480L);

        mockServer.verify();
    }

    @Test
    void calculate_whenTimeout_throwsGatewayTimeout_andDoesNotLogApiKey() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith("https://router.hereapi.com/v8/routes")))
                .andRespond(request -> {
                    throw new SocketTimeoutException("Read timed out");
                });

        assertThatThrownBy(() -> provider.calculate(sampleWaypoints()))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTING_PROVIDER_TIMEOUT);
                });

        // Kiểm tra log không chứa secret key
        List<ILoggingEvent> logs = listAppender.list;
        assertThat(logs).isNotEmpty();
        for (ILoggingEvent event : logs) {
            assertThat(event.getFormattedMessage()).doesNotContain("test-routing-key-secret-12345");
        }

        mockServer.verify();
    }

    @Test
    void calculate_whenInternalServerError500_throwsServiceUnavailable() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith("https://router.hereapi.com/v8/routes")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> provider.calculate(sampleWaypoints()))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTING_PROVIDER_UNAVAILABLE);
                });

        mockServer.verify();
    }

    @Test
    void calculate_whenMultiSectionLeg_groupsSectionsToCorrectDestinationStop() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith("https://router.hereapi.com/v8/routes")))
                .andRespond(withSuccess(new ClassPathResource("fixtures/here-route-multi-section-leg.json"), MediaType.APPLICATION_JSON));

        CalculatedRoute route = provider.calculate(sampleWaypoints());

        assertThat(route.sections()).hasSize(3);

        CalculatedSection sec1a = route.sections().get(0);
        assertThat(sec1a.sectionSequence()).isEqualTo(1);
        assertThat(sec1a.destinationStopSequence()).isEqualTo(2);
        assertThat(sec1a.baseTravelDurationSeconds()).isEqualTo(250L);

        CalculatedSection sec1b = route.sections().get(1);
        assertThat(sec1b.sectionSequence()).isEqualTo(2);
        assertThat(sec1b.destinationStopSequence()).isEqualTo(2);
        assertThat(sec1b.travelDurationSeconds()).isEqualTo(600L);
        assertThat(sec1b.baseTravelDurationSeconds()).isEqualTo(600L);

        CalculatedSection sec2 = route.sections().get(2);
        assertThat(sec2.sectionSequence()).isEqualTo(3);
        assertThat(sec2.destinationStopSequence()).isEqualTo(3);

        mockServer.verify();
    }

    @Test
    void calculate_whenNoRouteFound_throwsUnprocessableEntity() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith("https://router.hereapi.com/v8/routes")))
                .andRespond(withSuccess(new ClassPathResource("fixtures/here-route-no-route.json"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.calculate(sampleWaypoints()))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTE_NOT_FOUND_BY_PROVIDER);
                });

        mockServer.verify();
    }

    @Test
    void calculate_whenCriticalNotice_throwsUnprocessableEntity() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith("https://router.hereapi.com/v8/routes")))
                .andRespond(withSuccess(new ClassPathResource("fixtures/here-route-critical-notice.json"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.calculate(sampleWaypoints()))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTE_NOT_FOUND_BY_PROVIDER);
                });

        mockServer.verify();
    }

    @Test
    void calculate_whenUnauthorized_throwsServiceUnavailable() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith("https://router.hereapi.com/v8/routes")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> provider.calculate(sampleWaypoints()))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTING_UNAVAILABLE);
                });

        mockServer.verify();
    }

    @Test
    void calculate_whenRateLimitedOr5xx_throwsServiceUnavailable() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith("https://router.hereapi.com/v8/routes")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> provider.calculate(sampleWaypoints()))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTING_PROVIDER_UNAVAILABLE);
                });

        mockServer.verify();
    }

    @Test
    void calculate_whenMissingEndSection_throwsRoutingProviderException() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith("https://router.hereapi.com/v8/routes")))
                .andRespond(withSuccess(new ClassPathResource("fixtures/here-route-missing-end-section.json"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.calculate(sampleWaypoints()))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE);
                });

        mockServer.verify();
    }

    @Test
    void calculate_whenMissingDepartureTime_throwsRoutingProviderException() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith("https://router.hereapi.com/v8/routes")))
                .andRespond(withSuccess(new ClassPathResource("fixtures/here-route-missing-departure-time.json"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.calculate(List.of(
                new RoutingWaypoint(1L, "S1", new BigDecimal("10.8"), new BigDecimal("106.7"), 1, 0),
                new RoutingWaypoint(2L, "S2", new BigDecimal("10.9"), new BigDecimal("106.8"), 2, 0)
        )))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE);
                });

        mockServer.verify();
    }

    @Test
    void calculate_whenMalformedJsonPayload_throwsRoutingProviderException() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith("https://router.hereapi.com/v8/routes")))
                .andRespond(withSuccess("{ not-valid-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.calculate(sampleWaypoints()))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE);
                });

        mockServer.verify();
    }

    @Test
    void calculate_whenNegativeDistance_throwsRoutingProviderException() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith("https://router.hereapi.com/v8/routes")))
                .andRespond(withSuccess(new ClassPathResource("fixtures/here-route-negative-distance.json"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.calculate(List.of(
                new RoutingWaypoint(1L, "S1", new BigDecimal("10.8"), new BigDecimal("106.7"), 1, 0),
                new RoutingWaypoint(2L, "S2", new BigDecimal("10.9"), new BigDecimal("106.8"), 2, 0)
        )))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE);
                });

        mockServer.verify();
    }

    @Test
    void calculate_whenNegativeTravelDuration_throwsRoutingProviderException() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith("https://router.hereapi.com/v8/routes")))
                .andRespond(withSuccess(new ClassPathResource("fixtures/here-route-negative-travel-duration.json"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.calculate(List.of(
                new RoutingWaypoint(1L, "S1", new BigDecimal("10.8"), new BigDecimal("106.7"), 1, 0),
                new RoutingWaypoint(2L, "S2", new BigDecimal("10.9"), new BigDecimal("106.8"), 2, 0)
        )))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE);
                });

        mockServer.verify();
    }

    @Test
    void calculate_whenNegativeBaseDuration_throwsRoutingProviderException() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith("https://router.hereapi.com/v8/routes")))
                .andRespond(withSuccess(new ClassPathResource("fixtures/here-route-negative-base-duration.json"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.calculate(List.of(
                new RoutingWaypoint(1L, "S1", new BigDecimal("10.8"), new BigDecimal("106.7"), 1, 0),
                new RoutingWaypoint(2L, "S2", new BigDecimal("10.9"), new BigDecimal("106.8"), 2, 0)
        )))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE);
                });

        mockServer.verify();
    }

    @Test
    void calculate_whenNullRouteElement_throwsRoutingProviderException() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith("https://router.hereapi.com/v8/routes")))
                .andRespond(withSuccess("{\"routes\":[null]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.calculate(List.of(
                new RoutingWaypoint(1L, "S1", new BigDecimal("10.8"), new BigDecimal("106.7"), 1, 0),
                new RoutingWaypoint(2L, "S2", new BigDecimal("10.9"), new BigDecimal("106.8"), 2, 0)
        )))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE);
                });

        mockServer.verify();
    }

    @Test
    void calculate_whenNullSectionElement_throwsRoutingProviderException() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith("https://router.hereapi.com/v8/routes")))
                .andRespond(withSuccess("{\"routes\":[{\"sections\":[null]}]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.calculate(List.of(
                new RoutingWaypoint(1L, "S1", new BigDecimal("10.8"), new BigDecimal("106.7"), 1, 0),
                new RoutingWaypoint(2L, "S2", new BigDecimal("10.9"), new BigDecimal("106.8"), 2, 0)
        )))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE);
                });

        mockServer.verify();
    }

    @Test
    void calculate_whenNullNoticeElement_throwsRoutingProviderException() {
        String json = """
                {
                  "routes": [
                    {
                      "sections": [
                        {
                          "id": "sec-1",
                          "type": "vehicle",
                          "polyline": "BF",
                          "departure": { "time": "2026-09-10T08:00:00Z" },
                          "summary": { "length": 100, "duration": 60, "baseDuration": 60 },
                          "notices": [null]
                        }
                      ]
                    }
                  ]
                }
                """;
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith("https://router.hereapi.com/v8/routes")))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.calculate(List.of(
                new RoutingWaypoint(1L, "S1", new BigDecimal("10.8"), new BigDecimal("106.7"), 1, 0),
                new RoutingWaypoint(2L, "S2", new BigDecimal("10.9"), new BigDecimal("106.8"), 2, 0)
        )))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE);
                });

        mockServer.verify();
    }

    private List<RoutingWaypoint> sampleWaypoints() {
        return List.of(
                new RoutingWaypoint(1L, "Bến xe Miền Đông", new BigDecimal("10.801234"), new BigDecimal("106.710123"), 1, 0),
                new RoutingWaypoint(2L, "Ngã tư Hàng Xanh", new BigDecimal("10.800100"), new BigDecimal("106.711100"), 2, 120),
                new RoutingWaypoint(3L, "Chợ Bến Thành", new BigDecimal("10.772123"), new BigDecimal("106.698123"), 3, 0)
        );
    }
}
