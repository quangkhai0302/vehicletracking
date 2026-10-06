package com.quangkhai.vehicletracking_backend.reroute;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
import com.quangkhai.vehicletracking_backend.auth.repository.UserAccountRepository;
import com.quangkhai.vehicletracking_backend.config.CorsProperties;
import com.quangkhai.vehicletracking_backend.reroute.controller.RouteComparisonController;
import com.quangkhai.vehicletracking_backend.reroute.dto.RouteComparisonResponse;
import com.quangkhai.vehicletracking_backend.reroute.service.RouteComparisonService;

@WebMvcTest(controllers=RouteComparisonController.class,properties={"auth.security-enabled=true","app.cors.allowed-origins=http://localhost:5173"})
@EnableConfigurationProperties(CorsProperties.class)
@Import({SecurityConfig.class,SessionAccountValidationFilter.class})
class RouteComparisonControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean RouteComparisonService comparisons;
    @MockitoBean UserAccountRepository accounts;
    @MockitoBean org.springframework.security.crypto.password.PasswordEncoder passwords;

    MockHttpSession session(String role) {
        var principal=mock(SecurityConfig.UserAccountPrincipal.class);
        when(principal.accountId()).thenReturn(42L);
        when(principal.getPassword()).thenReturn("fixture-hash");
        when(accounts.isActiveForAuthentication(42L,"fixture-hash")).thenReturn(true);
        var session=new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(principal,null,List.of(new SimpleGrantedAuthority("ROLE_"+role)))));
        return session;
    }
    @Test void historyReadIsAdminOnlyAndReturnsExactRequestedRevision() throws Exception {
        mvc.perform(get("/api/v1/trips/7/revisions/41/comparison")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/trips/7/revisions/41/comparison").session(session("DRIVER"))).andExpect(status().isForbidden());
        verifyNoInteractions(comparisons);
        when(comparisons.find(7,41)).thenReturn(new RouteComparisonResponse(7,41,2,Instant.parse("2026-10-06T04:00:00Z"),
                "Đổi tuyến thử","UNAVAILABLE","Thiếu dữ liệu trước",1,null,null,null));
        mvc.perform(get("/api/v1/trips/7/revisions/41/comparison").session(session("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tripId").value(7))
                .andExpect(jsonPath("$.revisionId").value(41)).andExpect(jsonPath("$.status").value("UNAVAILABLE"));
        verify(comparisons).find(7,41);
    }
    @Test void unknownOrMismatchedRevisionReturns404() throws Exception {
        when(comparisons.find(8,41)).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        mvc.perform(get("/api/v1/trips/8/revisions/41/comparison").session(session("ADMIN"))).andExpect(status().isNotFound());
    }
}
