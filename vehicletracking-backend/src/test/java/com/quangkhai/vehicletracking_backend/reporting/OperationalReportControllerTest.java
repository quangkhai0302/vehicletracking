package com.quangkhai.vehicletracking_backend.reporting;

import com.quangkhai.vehicletracking_backend.reporting.controller.OperationalReportController;
import com.quangkhai.vehicletracking_backend.reporting.dto.OperationalReportResponse;
import com.quangkhai.vehicletracking_backend.reporting.service.OperationalReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OperationalReportController.class)
class OperationalReportControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean OperationalReportService service;

    @Test
    void operationsReturnsAggregatedMetricsForFilters() throws Exception {
        when(service.operations(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 21), 7L, 3L))
                .thenReturn(new OperationalReportResponse(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 21),
                        Instant.parse("2026-09-21T08:00:00Z"), 10, 8, 123_000, 72_000, 87.5, 1, 2, 3, 80));

        mvc.perform(get("/api/v1/reports/operations")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-21")
                        .param("vehicleId", "7")
                        .param("driverId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripCount").value(10))
                .andExpect(jsonPath("$.totalDistanceMeters").value(123000))
                .andExpect(jsonPath("$.onTimeRatePercent").value(87.5))
                .andExpect(jsonPath("$.overspeedEventCount").value(3));
    }
}
