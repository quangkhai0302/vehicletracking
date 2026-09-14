package com.quangkhai.vehicletracking_backend.traffic;

import com.quangkhai.vehicletracking_backend.reroute.service.RerouteEvaluationService;
import com.quangkhai.vehicletracking_backend.traffic.controller.TrafficEtaController;
import com.quangkhai.vehicletracking_backend.traffic.eta.TrafficEtaService;
import com.quangkhai.vehicletracking_backend.traffic.eta.TripEtaResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.quangkhai.vehicletracking_backend.config.CorsProperties;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = TrafficEtaController.class)
@EnableConfigurationProperties(CorsProperties.class)
class TrafficEtaControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean TrafficEtaService eta;
    @MockitoBean RerouteEvaluationService reroutes;

    @Test void reroutePersistenceFailureDoesNotTurnSuccessfulEtaIntoHttp500() throws Exception {
        var now = Instant.parse("2026-09-14T09:00:00Z");
        var response = new TripEtaResponse(4, 3, now, TrafficSource.HERE_LIVE, TrafficStatus.AVAILABLE,
                now, now, 2, 300, 320, List.of(), List.of(), null);
        when(eta.calculate(4)).thenReturn(response);
        doThrow(new org.hibernate.AssertionFailure("null identifier"))
                .when(reroutes).evaluate(4, response);
        mvc.perform(get("/api/v1/trips/4/eta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("HERE_LIVE"))
                .andExpect(jsonPath("$.totalRemainingSeconds").value(320));
        verify(reroutes).evaluate(4, response);
    }

    @Test void missingTripStillReturnsNotFound() throws Exception {
        when(eta.calculate(404)).thenThrow(new TrafficOperationException(org.springframework.http.HttpStatus.NOT_FOUND,
                TrafficErrorCode.TRIP_NOT_FOUND, "Không tìm thấy chuyến đi."));
        mvc.perform(get("/api/v1/trips/404/eta")).andExpect(status().isNotFound());
        verifyNoInteractions(reroutes);
    }
}
