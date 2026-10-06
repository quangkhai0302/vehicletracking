package com.quangkhai.vehicletracking_backend.reporting;
import com.quangkhai.vehicletracking_backend.reporting.controller.SimulationReportController;
import com.quangkhai.vehicletracking_backend.reporting.dto.*;
import com.quangkhai.vehicletracking_backend.reporting.service.SimulationReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(SimulationReportController.class)
class SimulationReportControllerTest {
    @Autowired MockMvc mvc; @MockitoBean SimulationReportService service;
    @Test void returnsSummaryAndPagingForSimulationMetric() throws Exception {
        when(service.report(null,null,7L,null,SimulationReportMetric.LATE,2,10)).thenReturn(
            new SimulationReportResponse(LocalDate.of(2026,9,1),LocalDate.of(2026,9,30),Instant.EPOCH,
            5,3,2,1000,150,50d,1,2,1,List.of(),2,10,1,1));
        mvc.perform(get("/api/v1/reports/simulation").param("vehicleId","7").param("metric","LATE").param("page","2").param("size","10"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.attemptCount").value(5))
            .andExpect(jsonPath("$.onTimeRatePercent").value(50)).andExpect(jsonPath("$.page").value(2))
            .andExpect(jsonPath("$.items").isArray());
    }
    @Test void unknownMetricAndMalformedDateReturn400() throws Exception {
        mvc.perform(get("/api/v1/reports/simulation").param("metric","BAD")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reports/simulation").param("from","bad-date")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}

