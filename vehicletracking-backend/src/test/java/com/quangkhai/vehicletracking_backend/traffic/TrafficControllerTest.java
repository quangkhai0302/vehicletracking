package com.quangkhai.vehicletracking_backend.traffic;

import com.quangkhai.vehicletracking_backend.traffic.controller.TrafficController;
import com.quangkhai.vehicletracking_backend.traffic.service.TrafficQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TrafficController.class, properties = {
        "app.cors.allowed-origins=http://localhost:5173"
})
@org.springframework.boot.context.properties.EnableConfigurationProperties(com.quangkhai.vehicletracking_backend.config.CorsProperties.class)
class TrafficControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrafficQueryService traffic;

    @Test
    void flow_returnsEnvelope() throws Exception {
        when(traffic.flow(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new TrafficEnvelope<>(TrafficSource.HERE_LIVE, TrafficStatus.AVAILABLE,
                        Instant.parse("2026-09-09T08:00:00Z"), Instant.parse("2026-09-09T08:00:01Z"), 1,
                        null, List.of(new TrafficFlowSegment("flow-1", "Nguyen Hue", 100,
                                List.of(List.of(10.77, 106.70), List.of(10.78, 106.71)), 30, 40, 3, "open", 1.0))));

        mockMvc.perform(get("/api/v1/traffic/flow")
                        .param("west", "106.64").param("south", "10.74")
                        .param("east", "106.74").param("north", "10.84"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.source").value("HERE_LIVE"))
                .andExpect(jsonPath("$.results[0].speedKmh").value(30));
    }

    @Test
    void flow_whenServiceRejectsBounds_returnsProblemCode() throws Exception {
        when(traffic.flow(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new TrafficOperationException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        TrafficErrorCode.TRAFFIC_BOUNDS_INVALID, "bounds invalid"));

        mockMvc.perform(get("/api/v1/traffic/flow")
                        .param("west", "106.74").param("south", "10.84")
                        .param("east", "106.64").param("north", "10.74"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRAFFIC_BOUNDS_INVALID"))
                .andExpect(jsonPath("$.title").value("TRAFFIC_BOUNDS_INVALID"));
    }

    @Test
    void tile_returnsPngImage() throws Exception {
        byte[] fakePng = new byte[]{1, 2, 3};
        when(traffic.tile(12, 3261, 1916)).thenReturn(fakePng);

        mockMvc.perform(get("/api/v1/traffic/tiles/12/3261/1916.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(fakePng));
    }

    @Test
    void mapTile_returnsStyleContentType() throws Exception {
        byte[] fakeJpeg = new byte[]{1, 2, 3};
        when(traffic.mapTile(HereMapStyle.SATELLITE, 12, 3261, 1916))
                .thenReturn(new TrafficProvider.RasterTile(fakeJpeg, MediaType.IMAGE_JPEG_VALUE));

        mockMvc.perform(get("/api/v1/traffic/map-tiles/SATELLITE/12/3261/1916"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(fakeJpeg));
    }
}
