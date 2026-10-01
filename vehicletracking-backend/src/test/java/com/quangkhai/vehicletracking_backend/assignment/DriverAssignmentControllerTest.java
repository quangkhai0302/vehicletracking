package com.quangkhai.vehicletracking_backend.assignment;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.quangkhai.vehicletracking_backend.assignment.controller.AssignmentValidationAdvice;
import com.quangkhai.vehicletracking_backend.assignment.dto.AssignmentActionResponse;
import com.quangkhai.vehicletracking_backend.assignment.entity.TripAssignmentStatus;
import com.quangkhai.vehicletracking_backend.assignment.service.TripAssignmentService;
import com.quangkhai.vehicletracking_backend.auth.config.SecurityConfig;
import com.quangkhai.vehicletracking_backend.auth.config.SessionAccountValidationFilter;
import com.quangkhai.vehicletracking_backend.auth.controller.AuthController;
import com.quangkhai.vehicletracking_backend.auth.repository.UserAccountRepository;
import com.quangkhai.vehicletracking_backend.auth.service.UserAccountService;
import com.quangkhai.vehicletracking_backend.config.CorsProperties;
import com.quangkhai.vehicletracking_backend.driverportal.controller.DriverAssignmentController;

import jakarta.servlet.http.Cookie;

@WebMvcTest(controllers = {DriverAssignmentController.class, AuthController.class}, properties = {
        "auth.security-enabled=true", "app.cors.allowed-origins=http://localhost:5173"})
@EnableConfigurationProperties(CorsProperties.class)
@Import({SecurityConfig.class, SessionAccountValidationFilter.class, AssignmentValidationAdvice.class})
class DriverAssignmentControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean TripAssignmentService service;
    @MockitoBean UserAccountRepository accounts;
    @MockitoBean UserAccountService userAccounts;
    @MockitoBean org.springframework.security.crypto.password.PasswordEncoder passwords;

    record Session(MockHttpSession value, SecurityConfig.UserAccountPrincipal principal) {}
    Session driverSession() {
        var principal = mock(SecurityConfig.UserAccountPrincipal.class);
        when(principal.accountId()).thenReturn(42L);
        when(principal.driverId()).thenReturn(9L);
        when(principal.getPassword()).thenReturn("fixture-hash");
        when(accounts.isActiveForAuthentication(42L, "fixture-hash")).thenReturn(true);
        var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_DRIVER")));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(authentication));
        return new Session(session, principal);
    }
    String csrf() throws Exception {
        return mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN").getValue();
    }

    @Test void requestListIsScopedToDriverRole() throws Exception {
        mvc.perform(get("/api/v1/driver/assignment-requests")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/driver/assignment-requests").session(driverSession().value()))
                .andExpect(status().isOk());
    }

    @Test void acceptAndDeclineDelegatePrincipalAndProtectMutationWithCsrf() throws Exception {
        var session = driverSession();
        UUID requestId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        when(service.accept(session.principal(), requestId))
                .thenReturn(new AssignmentActionResponse(requestId, TripAssignmentStatus.ACCEPTED, 7));
        String csrf = csrf();
        mvc.perform(post("/api/v1/driver/assignment-requests/" + requestId + "/accept")
                        .session(session.value()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/driver/assignment-requests/" + requestId + "/accept")
                        .session(session.value()).cookie(new Cookie("XSRF-TOKEN", csrf))
                        .header("X-XSRF-TOKEN", csrf))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"));
        verify(service).accept(session.principal(), requestId);
        mvc.perform(post("/api/v1/driver/assignment-requests/" + requestId + "/decline")
                        .session(session.value()).cookie(new Cookie("XSRF-TOKEN", csrf))
                        .header("X-XSRF-TOKEN", csrf).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"no\"}"))
                .andExpect(status().isBadRequest());
        verifyNoMoreInteractions(service);
    }
}
