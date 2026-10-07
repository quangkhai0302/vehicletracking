package com.quangkhai.vehicletracking_backend.dispatch;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.quangkhai.vehicletracking_backend.auth.config.SecurityConfig;
import com.quangkhai.vehicletracking_backend.auth.config.SessionAccountValidationFilter;
import com.quangkhai.vehicletracking_backend.auth.controller.AuthController;
import com.quangkhai.vehicletracking_backend.auth.entity.UserRole;
import com.quangkhai.vehicletracking_backend.auth.repository.UserAccountRepository;
import com.quangkhai.vehicletracking_backend.auth.service.UserAccountService;
import com.quangkhai.vehicletracking_backend.config.CorsProperties;
import com.quangkhai.vehicletracking_backend.dispatch.service.DriverDispatchService;
import com.quangkhai.vehicletracking_backend.driverportal.controller.DriverDispatchController;

import jakarta.servlet.http.Cookie;

@WebMvcTest(controllers = {DriverDispatchController.class, AuthController.class},
        properties = {"auth.security-enabled=true", "app.cors.allowed-origins=http://localhost:5173"})
@EnableConfigurationProperties(CorsProperties.class)
@Import({SecurityConfig.class, SessionAccountValidationFilter.class})
class DriverDispatchControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean DriverDispatchService driverService;
    @MockitoBean UserAccountRepository accounts;
    @MockitoBean UserAccountService userAccounts;
    @MockitoBean org.springframework.security.crypto.password.PasswordEncoder passwords;

    record Session(MockHttpSession value, SecurityConfig.UserAccountPrincipal principal) {}
    Session session(UserRole role) {
        var principal = mock(SecurityConfig.UserAccountPrincipal.class);
        when(principal.accountId()).thenReturn(42L);
        when(principal.getPassword()).thenReturn("fixture-hash");
        when(accounts.isActiveForAuthentication(42L, "fixture-hash")).thenReturn(true);
        var auth = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(auth));
        return new Session(session, principal);
    }
    String csrf() throws Exception {
        return mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn()
                .getResponse().getCookie("XSRF-TOKEN").getValue();
    }

    @Test void inboxKeepsRoleBoundaryAndScopedRead() throws Exception {
        mvc.perform(get("/api/v1/driver/dispatch/inbox")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/driver/dispatch/inbox").session(session(UserRole.ADMIN).value()))
                .andExpect(status().isForbidden());
        var driver = session(UserRole.DRIVER);
        when(driverService.inbox(driver.principal(), 50)).thenReturn(List.of());
        mvc.perform(get("/api/v1/driver/dispatch/inbox").session(driver.value()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(driverService).inbox(driver.principal(), 50);
    }

    @Test void markingInboxReadNeedsCsrfAndOwnItem() throws Exception {
        var driver = session(UserRole.DRIVER);
        mvc.perform(post("/api/v1/driver/dispatch/inbox/7/read").session(driver.value()))
                .andExpect(status().isForbidden());
        String token = csrf();
        when(driverService.markRead(driver.principal(), 7))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        mvc.perform(post("/api/v1/driver/dispatch/inbox/7/read").session(driver.value())
                .cookie(new Cookie("XSRF-TOKEN", token)).header("X-XSRF-TOKEN", token))
                .andExpect(status().isNotFound());
        verify(driverService).markRead(driver.principal(), 7);
    }

    @Test void deletingInboxItemNeedsCsrfAndUsesDriverScope() throws Exception {
        var driver = session(UserRole.DRIVER);
        mvc.perform(delete("/api/v1/driver/dispatch/inbox/7").session(driver.value()))
                .andExpect(status().isForbidden());
        String token = csrf();
        mvc.perform(delete("/api/v1/driver/dispatch/inbox/7").session(driver.value())
                .cookie(new Cookie("XSRF-TOKEN", token)).header("X-XSRF-TOKEN", token))
                .andExpect(status().isNoContent());
        verify(driverService).dismiss(driver.principal(), 7);
    }
}
