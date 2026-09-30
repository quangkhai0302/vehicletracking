package com.quangkhai.vehicletracking_backend.driverportal;

import com.quangkhai.vehicletracking_backend.auth.config.*;
import com.quangkhai.vehicletracking_backend.auth.controller.AuthController;
import com.quangkhai.vehicletracking_backend.auth.entity.UserRole;
import com.quangkhai.vehicletracking_backend.auth.repository.UserAccountRepository;
import com.quangkhai.vehicletracking_backend.auth.service.UserAccountService;
import com.quangkhai.vehicletracking_backend.config.CorsProperties;
import com.quangkhai.vehicletracking_backend.driverportal.controller.DriverNavigationController;
import com.quangkhai.vehicletracking_backend.driverportal.dto.DriverNavigationResponse;
import com.quangkhai.vehicletracking_backend.driverportal.service.DriverNavigationService;
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
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {DriverNavigationController.class, AuthController.class}, properties = {
        "auth.security-enabled=true", "app.cors.allowed-origins=http://localhost:5173"})
@EnableConfigurationProperties(CorsProperties.class)
@Import({SecurityConfig.class, SessionAccountValidationFilter.class})
class DriverNavigationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean DriverNavigationService service;
    @MockitoBean UserAccountRepository accounts;
    @MockitoBean UserAccountService userAccounts;
    @MockitoBean org.springframework.security.crypto.password.PasswordEncoder passwords;
    final UUID token = UUID.fromString("55b11618-b54c-4d04-af06-edb3f1721c65");

    record Session(MockHttpSession value, SecurityConfig.UserAccountPrincipal principal) {}
    Session session(UserRole role) {
        var principal = mock(SecurityConfig.UserAccountPrincipal.class);
        when(principal.accountId()).thenReturn(42L); when(principal.getPassword()).thenReturn("fixture-hash");
        when(accounts.isActiveForAuthentication(42L, "fixture-hash")).thenReturn(true);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, new SecurityContextImpl(auth));
        return new Session(session, principal);
    }
    String csrf() throws Exception {
        return mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn()
                .getResponse().getCookie("XSRF-TOKEN").getValue();
    }
    DriverNavigationResponse response() {
        return new DriverNavigationResponse(Instant.parse("2026-09-29T00:00:00Z"), null, List.of(), null, null, null, null, null,
                new com.quangkhai.vehicletracking_backend.checkin.dto.TripCheckInsResponse(7, 0, 1, false, List.of()), List.of());
    }

    @Test void anonymousAndAdminCannotUseDriverNavigation() throws Exception {
        mvc.perform(get("/api/v1/driver/trips/7/navigation")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/driver/trips/7/navigation").session(session(UserRole.ADMIN).value())).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void driverCannotReadGlobalAdminTelemetry() throws Exception {
        var driver = session(UserRole.DRIVER);
        mvc.perform(get("/api/v1/telemetry/snapshot").session(driver.value())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/telemetry/stream").session(driver.value())).andExpect(status().isForbidden());
    }
    @Test void scopedNavigationPassesAuthenticatedPrincipal() throws Exception {
        var driver = session(UserRole.DRIVER); when(service.navigation(driver.principal(), 7)).thenReturn(response());
        mvc.perform(get("/api/v1/driver/trips/7/navigation").session(driver.value()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.serverTime").value("2026-09-29T00:00:00Z"))
                .andExpect(jsonPath("$.checkIns.tripId").value(7)).andExpect(jsonPath("$.stations").isArray());
        verify(service).navigation(driver.principal(), 7);
    }
    @Test void startAndOptionsRequireCsrf() throws Exception {
        var driver = session(UserRole.DRIVER);
        mvc.perform(post("/api/v1/driver/trips/7/start").session(driver.value())).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/driver/trips/7/route-options").session(driver.value())).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void csrfProtectedStartDelegatesOnlyOnce() throws Exception {
        var driver = session(UserRole.DRIVER); String csrf = csrf(); when(service.start(driver.principal(), 7)).thenReturn(response());
        mvc.perform(post("/api/v1/driver/trips/7/start").session(driver.value())
                .cookie(new Cookie("XSRF-TOKEN", csrf)).header("X-XSRF-TOKEN", csrf)).andExpect(status().isOk());
        verify(service).start(driver.principal(), 7);
    }
    @Test void applyValidatesTokenAndOptionIndex() throws Exception {
        var driver = session(UserRole.DRIVER); String csrf = csrf();
        for (String body : List.of("{}", "{\"optionIndex\":-1}", "{\"optionIndex\":3}", "{\"optionIndex\":null}")) {
            mvc.perform(post("/api/v1/driver/trips/7/route-options/" + token + "/apply").session(driver.value())
                    .cookie(new Cookie("XSRF-TOKEN", csrf)).header("X-XSRF-TOKEN", csrf)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/driver/trips/7/route-options/not-a-uuid/apply").session(driver.value())
                .cookie(new Cookie("XSRF-TOKEN", csrf)).header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON).content("{\"optionIndex\":0}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void applyDoesNotTrustClientDriverOrGeometry() throws Exception {
        var driver = session(UserRole.DRIVER); String csrf = csrf();
        when(service.apply(driver.principal(), 7, token, 1)).thenReturn(response());
        mvc.perform(post("/api/v1/driver/trips/7/route-options/" + token + "/apply").session(driver.value())
                .cookie(new Cookie("XSRF-TOKEN", csrf)).header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON).content("{\"optionIndex\":1,\"driverId\":999,\"sections\":[]}"))
                .andExpect(status().isOk());
        verify(service).apply(driver.principal(), 7, token, 1);
    }
}
