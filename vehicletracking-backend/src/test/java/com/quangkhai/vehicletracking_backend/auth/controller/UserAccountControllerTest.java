package com.quangkhai.vehicletracking_backend.auth.controller;

import com.quangkhai.vehicletracking_backend.auth.dto.DriverPasswordResetRequest;
import com.quangkhai.vehicletracking_backend.auth.service.UserAccountService;
import com.quangkhai.vehicletracking_backend.config.CorsProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = UserAccountController.class,
        properties = {
                "auth.security-enabled=false",
                "app.cors.allowed-origins=http://localhost:5173"
        })
@EnableConfigurationProperties(CorsProperties.class)
class UserAccountControllerTest {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    UserAccountService accounts;

    @Test
    void resetDriverPasswordReturnsNoContentAndDelegates() throws Exception {
        mvc.perform(post("/api/v1/users/7/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"new-password\"}"))
                .andExpect(status().isNoContent());

        ArgumentCaptor<DriverPasswordResetRequest> input =
                ArgumentCaptor.forClass(DriverPasswordResetRequest.class);
        verify(accounts).resetDriverPassword(eq(7L), input.capture());
        assertThat(input.getValue().password()).isEqualTo("new-password");
    }

    @Test
    void resetDriverPasswordRejectsShortPasswordBeforeService() throws Exception {
        mvc.perform(post("/api/v1/users/7/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accounts);
    }
}
