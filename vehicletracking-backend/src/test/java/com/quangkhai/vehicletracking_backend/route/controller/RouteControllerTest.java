package com.quangkhai.vehicletracking_backend.route.controller;

import com.quangkhai.vehicletracking_backend.config.CorsProperties;
import com.quangkhai.vehicletracking_backend.route.dto.RouteCreateRequest;
import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
import com.quangkhai.vehicletracking_backend.route.dto.RouteSummaryResponse;
import com.quangkhai.vehicletracking_backend.route.entity.RouteTransportMode;
import com.quangkhai.vehicletracking_backend.route.entity.RoutingProviderName;
import com.quangkhai.vehicletracking_backend.route.error.RouteErrorCode;
import com.quangkhai.vehicletracking_backend.route.error.RouteExceptionHandler;
import com.quangkhai.vehicletracking_backend.route.error.RouteOperationException;
import com.quangkhai.vehicletracking_backend.route.service.RouteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RouteController.class, properties = {
        "app.cors.allowed-origins=http://localhost:5173"
})
@Import(RouteExceptionHandler.class)
@EnableConfigurationProperties(CorsProperties.class)
class RouteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RouteService routeService;

    @Test
    void findAll_returnsRouteSummaryList() throws Exception {
        when(routeService.findAll()).thenReturn(List.of(sampleSummary()));

        mockMvc.perform(get("/api/v1/routes"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].name").value("Tuyến Quận 1 - Thủ Đức"))
                .andExpect(jsonPath("$[0].stopCount").value(3))
                .andExpect(jsonPath("$[0].totalDistanceMeters").value(12500))
                .andExpect(jsonPath("$[0].estimatedTripDurationSeconds").value(1920));
    }

    @Test
    void findById_existing_returns200AndDetail() throws Exception {
        when(routeService.findById(10L)).thenReturn(sampleDetail());

        mockMvc.perform(get("/api/v1/routes/10"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("Tuyến Quận 1 - Thủ Đức"))
                .andExpect(jsonPath("$.stops").isArray())
                .andExpect(jsonPath("$.stops.length()").value(2))
                .andExpect(jsonPath("$.sections").isArray())
                .andExpect(jsonPath("$.sections.length()").value(1));
    }

    @Test
    void findById_notFound_returns404() throws Exception {
        when(routeService.findById(999L)).thenThrow(
                new RouteOperationException(HttpStatus.NOT_FOUND, RouteErrorCode.ROUTE_NOT_FOUND, "Không tìm thấy tuyến đường với ID: 999")
        );

        mockMvc.perform(get("/api/v1/routes/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROUTE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Không tìm thấy tuyến đường với ID: 999"));
    }

    @Test
    void create_validPayload_returns201AndLocationAndBody() throws Exception {
        when(routeService.create(any(RouteCreateRequest.class))).thenReturn(sampleDetail());

        String validPayload = """
                {
                  "name": "Tuyến Quận 1 - Thủ Đức",
                  "stops": [
                    { "stationId": 1, "dwellDurationSeconds": 0 },
                    { "stationId": 2, "dwellDurationSeconds": 60 }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/routes/10"))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("Tuyến Quận 1 - Thủ Đức"));

        verify(routeService).create(any(RouteCreateRequest.class));
    }

    @Test
    void update_validPayload_returns200AndKeepsRouteId() throws Exception {
        when(routeService.update(org.mockito.ArgumentMatchers.eq(10L), any(RouteCreateRequest.class)))
                .thenReturn(sampleDetail());

        String validPayload = """
                {
                  "name": "Tuyến Quận 1 - Thủ Đức (đã cập nhật)",
                  "stops": [
                    { "stationId": 1, "dwellDurationSeconds": 0 },
                    { "stationId": 2, "dwellDurationSeconds": 0 }
                  ]
                }
                """;

        mockMvc.perform(put("/api/v1/routes/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("Tuyến Quận 1 - Thủ Đức"));

        verify(routeService).update(org.mockito.ArgumentMatchers.eq(10L), any(RouteCreateRequest.class));
    }

    @Test
    void create_invalidPayload_stopsLessThan2_returns400() throws Exception {
        String invalidPayload = """
                {
                  "name": "Tuyến lỗi",
                  "stops": [
                    { "stationId": 1, "dwellDurationSeconds": 0 }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROUTE_VALIDATION_FAILED"));

        verifyNoInteractions(routeService);
    }

    @Test
    void create_nullStopItem_returns400() throws Exception {
        String invalidPayload = """
                {
                  "name": "Tuyến có stop null",
                  "stops": [
                    null,
                    { "stationId": 2, "dwellDurationSeconds": 0 }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROUTE_VALIDATION_FAILED"));

        verifyNoInteractions(routeService);
    }

    @Test
    void create_negativeDwell_returns400() throws Exception {
        String invalidPayload = """
                {
                  "name": "Tuyến dwell âm",
                  "stops": [
                    { "stationId": 1, "dwellDurationSeconds": 0 },
                    { "stationId": 2, "dwellDurationSeconds": -10 }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROUTE_VALIDATION_FAILED"));

        verifyNoInteractions(routeService);
    }

    @Test
    void create_dwellOver3600_returns400() throws Exception {
        String invalidPayload = """
                {
                  "name": "Tuyến dwell quá 3600s",
                  "stops": [
                    { "stationId": 1, "dwellDurationSeconds": 0 },
                    { "stationId": 2, "dwellDurationSeconds": 3601 }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROUTE_VALIDATION_FAILED"));

        verifyNoInteractions(routeService);
    }

    @Test
    void create_nameOver150Chars_returns400() throws Exception {
        String invalidPayload = String.format("""
                {
                  "name": "%s",
                  "stops": [
                    { "stationId": 1, "dwellDurationSeconds": 0 },
                    { "stationId": 2, "dwellDurationSeconds": 0 }
                  ]
                }
                """, "A".repeat(151));

        mockMvc.perform(post("/api/v1/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROUTE_VALIDATION_FAILED"));

        verifyNoInteractions(routeService);
    }

    @Test
    void create_stopsMoreThan50_returns400() throws Exception {
        StringBuilder stopsBuilder = new StringBuilder("[");
        for (int i = 1; i <= 51; i++) {
            stopsBuilder.append(String.format("{\"stationId\": %d, \"dwellDurationSeconds\": 0}", i));
            if (i < 51) stopsBuilder.append(",");
        }
        stopsBuilder.append("]");

        String invalidPayload = String.format("""
                {
                  "name": "Tuyến 51 trạm",
                  "stops": %s
                }
                """, stopsBuilder);

        mockMvc.perform(post("/api/v1/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROUTE_VALIDATION_FAILED"));

        verifyNoInteractions(routeService);
    }

    @Test
    void create_stationUnavailable_returns422() throws Exception {
        when(routeService.create(any(RouteCreateRequest.class))).thenThrow(
                new RouteOperationException(HttpStatus.UNPROCESSABLE_ENTITY, RouteErrorCode.ROUTE_STATION_UNAVAILABLE, "Không tìm thấy hoặc trạm không hoạt động: [999]")
        );

        String payload = """
                {
                  "name": "Tuyến lỗi",
                  "stops": [
                    { "stationId": 1, "dwellDurationSeconds": 0 },
                    { "stationId": 999, "dwellDurationSeconds": 60 }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ROUTE_STATION_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value("Không tìm thấy hoặc trạm không hoạt động: [999]"));
    }

    @Test
    void create_routingDisabled_returns503() throws Exception {
        when(routeService.create(any(RouteCreateRequest.class))).thenThrow(
                new RouteOperationException(HttpStatus.SERVICE_UNAVAILABLE, RouteErrorCode.ROUTING_UNAVAILABLE, "HERE Routing đang bị tắt")
        );

        String payload = """
                {
                  "name": "Tuyến",
                  "stops": [
                    { "stationId": 1, "dwellDurationSeconds": 0 },
                    { "stationId": 2, "dwellDurationSeconds": 60 }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ROUTING_UNAVAILABLE"));
    }

    @Test
    void create_routingTimeout_returns504() throws Exception {
        when(routeService.create(any(RouteCreateRequest.class))).thenThrow(
                new RouteOperationException(HttpStatus.GATEWAY_TIMEOUT, RouteErrorCode.ROUTING_PROVIDER_TIMEOUT, "Quá thời gian kết nối HERE Routing API")
        );

        String payload = """
                {
                  "name": "Tuyến",
                  "stops": [
                    { "stationId": 1, "dwellDurationSeconds": 0 },
                    { "stationId": 2, "dwellDurationSeconds": 60 }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("ROUTING_PROVIDER_TIMEOUT"));
    }

    private RouteSummaryResponse sampleSummary() {
        return new RouteSummaryResponse(
                10L,
                "Tuyến Quận 1 - Thủ Đức",
                RouteTransportMode.CAR,
                RoutingProviderName.HERE,
                "Trạm Bến Thành",
                "Trạm Suối Tiên",
                3,
                12500L,
                1800L,
                120L,
                1920L,
                Instant.parse("2026-09-11T07:00:00Z"),
                Instant.parse("2026-09-11T07:00:00Z")
        );
    }

    private RouteDetailResponse sampleDetail() {
        RouteDetailResponse.RouteStopResponse s1 = new RouteDetailResponse.RouteStopResponse(
                1, "START", 1L, "Trạm 1", new BigDecimal("10.800000"), new BigDecimal("106.700000"), 0, 0L, 0L, 0L, 0L
        );
        RouteDetailResponse.RouteStopResponse s2 = new RouteDetailResponse.RouteStopResponse(
                2, "END", 2L, "Trạm 2", new BigDecimal("10.850000"), new BigDecimal("106.750000"), 60, 12500L, 1680L, 1680L, 1740L
        );

        RouteDetailResponse.RouteSectionResponse sec = new RouteDetailResponse.RouteSectionResponse(
                1, 2, "polyline_mock", 12500L, 1680L, 1680L
        );

        return new RouteDetailResponse(
                10L,
                "Tuyến Quận 1 - Thủ Đức",
                RouteTransportMode.CAR,
                RoutingProviderName.HERE,
                12500L,
                1680L,
                1680L,
                60L,
                1740L,
                Instant.parse("2026-09-11T07:00:00Z"),
                Instant.parse("2026-09-11T07:00:00Z"),
                Instant.parse("2026-09-11T07:00:00Z"),
                List.of(s1, s2),
                List.of(sec)
        );
    }
}
