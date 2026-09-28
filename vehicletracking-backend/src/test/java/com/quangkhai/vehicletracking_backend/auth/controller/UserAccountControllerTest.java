package com.quangkhai.vehicletracking_backend.auth.controller;

import com.quangkhai.vehicletracking_backend.auth.dto.DriverAccountCreateRequest;
import com.quangkhai.vehicletracking_backend.auth.dto.DriverAccountCreatedResponse;
import com.quangkhai.vehicletracking_backend.auth.dto.DriverPasswordResetRequest;
import com.quangkhai.vehicletracking_backend.auth.entity.UserRole;
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
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
                        .content("{\"password\":\"pass1234\"}"))
                .andExpect(status().isNoContent());

        ArgumentCaptor<DriverPasswordResetRequest> input =
                ArgumentCaptor.forClass(DriverPasswordResetRequest.class);
        verify(accounts).resetDriverPassword(eq(7L), input.capture());
        assertThat(input.getValue().password()).isEqualTo("pass1234");
    }

    @Test
    void resetDriverPasswordRejectsShortPasswordBeforeService() throws Exception {
        mvc.perform(post("/api/v1/users/7/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accounts);
    }

    @Test
    void createDriverAccountOnlyRequiresDriverAndReturnsGeneratedCredentials() throws Exception {
        DriverAccountCreatedResponse created = new DriverAccountCreatedResponse(9L, "khainq",
                UserRole.DRIVER, true, 7L, "Nguyễn Quang Khải", "B2-123", "Tmp8Pass");
        when(accounts.createDriverAccount(new DriverAccountCreateRequest(7L))).thenReturn(created);

        mvc.perform(post("/api/v1/users/driver")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverId\":7}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("khainq"))
                .andExpect(jsonPath("$.temporaryPassword").value("Tmp8Pass"))
                .andExpect(jsonPath("$.driverId").value(7));

        verify(accounts).createDriverAccount(new DriverAccountCreateRequest(7L));
    }

    @Test
    void createDriverAccountRejectsMissingDriverBeforeService() throws Exception {
        mvc.perform(post("/api/v1/users/driver")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accounts);
    }
}
