package com.quangkhai.vehicletracking_backend.dispatch;

import com.quangkhai.vehicletracking_backend.auth.config.*;
import com.quangkhai.vehicletracking_backend.auth.controller.AuthController;
import com.quangkhai.vehicletracking_backend.auth.entity.UserRole;
import com.quangkhai.vehicletracking_backend.auth.repository.UserAccountRepository;
import com.quangkhai.vehicletracking_backend.auth.service.UserAccountService;
import com.quangkhai.vehicletracking_backend.config.CorsProperties;
import com.quangkhai.vehicletracking_backend.dispatch.controller.*;
import com.quangkhai.vehicletracking_backend.dispatch.dto.DriverDispatchDetail;
import com.quangkhai.vehicletracking_backend.dispatch.entity.*;
import com.quangkhai.vehicletracking_backend.dispatch.service.*;
import com.quangkhai.vehicletracking_backend.driverportal.controller.DriverDispatchController;
import jakarta.servlet.http.Cookie;
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
import java.time.Instant;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {DriverDispatchController.class, AdminTripDispatchController.class, AuthController.class},
        properties = {"auth.security-enabled=true", "app.cors.allowed-origins=http://localhost:5173"})
@EnableConfigurationProperties(CorsProperties.class)
@Import({SecurityConfig.class, SessionAccountValidationFilter.class, DispatchValidationAdvice.class})
class DriverDispatchControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean DriverDispatchService driverService;
    @MockitoBean AdminTripDispatchService adminService;
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
    DriverDispatchDetail detail() {
        return new DriverDispatchDetail(7, DispatchStartMode.AUTO_IF_READY, DispatchState.WAITING_READY,
                null, null, Instant.parse("2026-10-01T08:15:00Z"), 2, true, true);
    }

    @Test void roleBoundaryAndScopedRead() throws Exception {
        mvc.perform(get("/api/v1/driver/trips/7/dispatch")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/driver/trips/7/dispatch").session(session(UserRole.ADMIN).value()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/trips/7/dispatch").session(session(UserRole.DRIVER).value()))
                .andExpect(status().isForbidden());
        var driver = session(UserRole.DRIVER);
        when(driverService.detail(driver.principal(), 7)).thenReturn(detail());
        mvc.perform(get("/api/v1/driver/trips/7/dispatch").session(driver.value()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tripId").value(7))
                .andExpect(jsonPath("$.state").value("WAITING_READY"));
        verify(driverService).detail(driver.principal(), 7);
    }

    @Test void mutationNeedsCsrfAndReturnsStableConflictCode() throws Exception {
        var driver = session(UserRole.DRIVER);
        mvc.perform(post("/api/v1/driver/trips/7/dispatch/ready").session(driver.value())
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":2}"))
                .andExpect(status().isForbidden());
        String token = csrf();
        when(driverService.ready(driver.principal(), 7, 2))
                .thenThrow(DispatchProblemException.conflict("DISPATCH_STALE_REVISION"));
        mvc.perform(post("/api/v1/driver/trips/7/dispatch/ready").session(driver.value())
                .cookie(new Cookie("XSRF-TOKEN", token)).header("X-XSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":2}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("DISPATCH_STALE_REVISION"));
    }

    @Test void invalidRequestHasValidationCode() throws Exception {
        var driver = session(UserRole.DRIVER);
        String token = csrf();
        mvc.perform(post("/api/v1/driver/trips/7/dispatch/ready").session(driver.value())
                .cookie(new Cookie("XSRF-TOKEN", token)).header("X-XSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DISPATCH_VALIDATION_FAILED"));
        verifyNoInteractions(driverService);
    }
}
