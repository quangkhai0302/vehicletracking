package com.quangkhai.vehicletracking_backend.auth.config;

import com.quangkhai.vehicletracking_backend.auth.entity.UserAccountEntity;
import com.quangkhai.vehicletracking_backend.auth.entity.UserRole;
import com.quangkhai.vehicletracking_backend.auth.repository.UserAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionAccountValidationFilterTest {
    @Mock UserAccountRepository accounts;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsAndClearsAStaleSessionImmediately() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(authenticationFor(7L));
        when(accounts.isActiveForAuthentication(7L, "hash")).thenReturn(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new SessionAccountValidationFilter(accounts).doFilter(
                new MockHttpServletRequest("GET", "/api/v1/auth/me"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void keepsAnActiveSessionForTheRemainingSecurityChain() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(authenticationFor(8L));
        when(accounts.isActiveForAuthentication(8L, "hash")).thenReturn(true);
        MockFilterChain chain = new MockFilterChain();

        new SessionAccountValidationFilter(accounts).doFilter(
                new MockHttpServletRequest("GET", "/api/v1/auth/me"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @ParameterizedTest
    @CsvSource({
            "GET,/api/v1/driver/trips", "GET,/api/v1/trips", "POST,/api/v1/auth/register-admin",
            "PUT,/api/v1/auth/me", "GET,/api/v1/auth/change-password", "GET,/api/v1/auth/me/",
            "GET,/api/v1/auth/me/../driver/trips", "GET,/api/v1/auth/me;ignored=true",
            "POST,/api/v1/auth/change-password/", "GET,/unknown"
    })
    void temporaryPasswordRejectsEveryRequestOutsideTheExactLifecycleAllowlist(String method, String path)
            throws Exception {
        SecurityContextHolder.getContext().setAuthentication(driverAuthentication(true));
        when(accounts.isActiveForAuthentication(9L, "hash")).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new SessionAccountValidationFilter(accounts).doFilter(new MockHttpServletRequest(method, path), response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString()).contains("PASSWORD_CHANGE_REQUIRED", "đổi mật khẩu");
        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @ParameterizedTest
    @CsvSource({"GET,/api/v1/auth/me", "GET,/api/v1/auth/csrf", "GET,/api/v1/health",
            "POST,/api/v1/auth/login", "POST,/api/v1/auth/logout", "POST,/api/v1/auth/change-password"})
    void temporaryPasswordCanReachLifecycleEndpoints(String method, String path) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(driverAuthentication(true));
        when(accounts.isActiveForAuthentication(9L, "hash")).thenReturn(true);
        MockFilterChain chain = new MockFilterChain();

        new SessionAccountValidationFilter(accounts).doFilter(new MockHttpServletRequest(method, path),
                new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void oldCredentialWinsOverTemporaryPasswordGateAndInvalidatesSession() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(driverAuthentication(true));
        when(accounts.isActiveForAuthentication(9L, "hash")).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/driver/trips");
        var session = request.getSession();
        MockHttpServletResponse response = new MockHttpServletResponse();

        new SessionAccountValidationFilter(accounts).doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThatThrownBy(() -> session.getAttribute("any")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void regularDriverCanReachBusinessEndpoints() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(driverAuthentication(false));
        when(accounts.isActiveForAuthentication(9L, "hash")).thenReturn(true);
        MockFilterChain chain = new MockFilterChain();

        new SessionAccountValidationFilter(accounts).doFilter(
                new MockHttpServletRequest("GET", "/api/v1/driver/trips"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void lifecycleAllowlistWorksWithinServletContextPath() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(driverAuthentication(true));
        when(accounts.isActiveForAuthentication(9L, "hash")).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/vehicletracking/api/v1/auth/me");
        request.setContextPath("/vehicletracking");
        MockFilterChain chain = new MockFilterChain();

        new SessionAccountValidationFilter(accounts).doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    private UsernamePasswordAuthenticationToken driverAuthentication(boolean required) {
        UserAccountEntity account = new UserAccountEntity("driver", "hash", UserRole.DRIVER, null);
        ReflectionTestUtils.setField(account, "id", 9L);
        if (!required) account.changePassword("hash");
        var principal = SecurityConfig.UserAccountPrincipal.from(account);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }

    private UsernamePasswordAuthenticationToken authenticationFor(long id) {
        UserAccountEntity account = new UserAccountEntity("admin", "hash", UserRole.ADMIN, null);
        ReflectionTestUtils.setField(account, "id", id);
        var principal = SecurityConfig.UserAccountPrincipal.from(account);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }
}
