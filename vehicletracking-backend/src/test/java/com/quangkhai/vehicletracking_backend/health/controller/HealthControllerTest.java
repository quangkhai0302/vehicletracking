package com.quangkhai.vehicletracking_backend.health.controller;

import com.quangkhai.vehicletracking_backend.auth.config.SecurityConfig;
import com.quangkhai.vehicletracking_backend.auth.config.SessionAccountValidationFilter;
import com.quangkhai.vehicletracking_backend.auth.repository.UserAccountRepository;
import com.quangkhai.vehicletracking_backend.config.CorsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HealthController.class, properties = {
        "auth.security-enabled=true", "app.cors.allowed-origins=http://localhost:5173"
})
@EnableConfigurationProperties(CorsProperties.class)
@Import({SecurityConfig.class, SessionAccountValidationFilter.class})
class HealthControllerTest {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    UserAccountRepository accounts;

    @MockitoBean
    PasswordEncoder passwords;

    @Test
    void healthEndpointIsPublicAndDoesNotAccessAccountData() throws Exception {
        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        verifyNoInteractions(accounts);
    }

    @Test
    void operationalEndpointsRemainProtected() throws Exception {
        mvc.perform(get("/api/v1/telemetry/snapshot"))
                .andExpect(status().isUnauthorized());
    }
}
