package com.quangkhai.vehicletracking_backend.dashboard;

import com.quangkhai.vehicletracking_backend.dashboard.controller.DashboardController;
import com.quangkhai.vehicletracking_backend.dashboard.dto.DashboardSummaryResponse;
import com.quangkhai.vehicletracking_backend.dashboard.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
class DashboardControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean DashboardService service;

    @Test
    void summaryReturnsOperationalMetrics() throws Exception {
        when(service.summary()).thenReturn(new DashboardSummaryResponse(
                Instant.parse("2026-09-21T08:00:00Z"), 4, 3, 2, 5, 8, 1, 1, 1, 2, List.of()));

        mvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeVehicleCount").value(4))
                .andExpect(jsonPath("$.tripsInProgress").value(2))
                .andExpect(jsonPath("$.overdueTrips").value(1))
                .andExpect(jsonPath("$.offRouteVehicleCount").value(1))
                .andExpect(jsonPath("$.unreadAlertCount").value(2));
    }
}
