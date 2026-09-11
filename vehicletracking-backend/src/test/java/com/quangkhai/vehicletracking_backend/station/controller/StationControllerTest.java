package com.quangkhai.vehicletracking_backend.station.controller;

import com.quangkhai.vehicletracking_backend.config.CorsProperties;
import com.quangkhai.vehicletracking_backend.station.dto.StationResponse;
import com.quangkhai.vehicletracking_backend.station.service.StationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StationController.class, properties = {
        "app.cors.allowed-origins=http://localhost:5173"
})
@EnableConfigurationProperties(CorsProperties.class)
class StationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StationService stationService;

    @Test
    void findAll_returnsActiveStations() throws Exception {
        when(stationService.findAll()).thenReturn(List.of(sampleStation()));

        mockMvc.perform(get("/api/v1/stations"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Bến xe Miền Đông"))
                .andExpect(jsonPath("$[0].latitude").value(10.801234))
                .andExpect(jsonPath("$[0].longitude").value(106.710123))
                .andExpect(jsonPath("$[0].checkinRadiusMeters").value(50));
    }

    @Test
    void create_withValidPayload_returns201AndLocation() throws Exception {
        when(stationService.create(any())).thenReturn(sampleStation());

        mockMvc.perform(post("/api/v1/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/stations/1"))
                .andExpect(jsonPath("$.name").value("Bến xe Miền Đông"));

        verify(stationService).create(any());
    }

    @Test
    void create_withInvalidCoordinates_returns400WithoutCallingService() throws Exception {
        String invalidPayload = """
                {
                  "name": "Trạm không hợp lệ",
                  "address": null,
                  "latitude": 91,
                  "longitude": 181,
                  "checkinRadiusMeters": 5
                }
                """;

        mockMvc.perform(post("/api/v1/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(stationService);
    }

    @Test
    void update_withValidPayload_returnsUpdatedStation() throws Exception {
        StationResponse updated = new StationResponse(
                1L,
                "Trạm đã cập nhật",
                null,
                new BigDecimal("10.810000"),
                new BigDecimal("106.720000"),
                75,
                true,
                Instant.parse("2026-09-10T04:00:00Z"),
                Instant.parse("2026-09-10T05:00:00Z")
        );
        when(stationService.update(eq(1L), any())).thenReturn(updated);

        mockMvc.perform(put("/api/v1/stations/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Trạm đã cập nhật"))
                .andExpect(jsonPath("$.checkinRadiusMeters").value(75));
    }

    @Test
    void findById_whenStationDoesNotExist_returns404() throws Exception {
        when(stationService.findById(999L))
                .thenThrow(new ResponseStatusException(NOT_FOUND, "Station 999 was not found"));

        mockMvc.perform(get("/api/v1/stations/999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void delete_existingStation_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/stations/1"))
                .andExpect(status().isNoContent());

        verify(stationService).delete(1L);
    }

    private StationResponse sampleStation() {
        return new StationResponse(
                1L,
                "Bến xe Miền Đông",
                "292 Đinh Bộ Lĩnh, Bình Thạnh, TP.HCM",
                new BigDecimal("10.801234"),
                new BigDecimal("106.710123"),
                50,
                true,
                Instant.parse("2026-09-10T04:00:00Z"),
                Instant.parse("2026-09-10T04:00:00Z")
        );
    }

    private String validPayload() {
        return """
                {
                  "name": "Bến xe Miền Đông",
                  "address": "292 Đinh Bộ Lĩnh, Bình Thạnh, TP.HCM",
                  "latitude": 10.801234,
                  "longitude": 106.710123,
                  "checkinRadiusMeters": 50
                }
                """;
    }
}
