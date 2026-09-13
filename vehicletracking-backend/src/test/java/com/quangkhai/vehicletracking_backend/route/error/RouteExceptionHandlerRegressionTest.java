package com.quangkhai.vehicletracking_backend.route.error;

import com.quangkhai.vehicletracking_backend.config.CorsProperties;
import com.quangkhai.vehicletracking_backend.station.controller.StationController;
import com.quangkhai.vehicletracking_backend.station.service.StationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StationController.class, properties = {
        "app.cors.allowed-origins=http://localhost:5173"
})
@Import(RouteExceptionHandler.class)
@EnableConfigurationProperties(CorsProperties.class)
class RouteExceptionHandlerRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StationService stationService;

    @Test
    void stationValidationFailure_isNotInterceptedByRouteExceptionHandler() throws Exception {
        String invalidPayload = """
                {
                  "name": "",
                  "latitude": 91,
                  "longitude": 181,
                  "checkinRadiusMeters": 5
                }
                """;

        mockMvc.perform(post("/api/v1/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                // RouteExceptionHandler must not intercept station controller:
                // If it intercepted, jsonPath("$.code") would be "ROUTE_VALIDATION_FAILED".
                .andExpect(jsonPath("$.code").doesNotExist());
    }
}
