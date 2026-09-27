package com.quangkhai.vehicletracking_backend.auth.config;

import com.quangkhai.vehicletracking_backend.auth.entity.UserAccountEntity;
import com.quangkhai.vehicletracking_backend.auth.entity.UserRole;
import com.quangkhai.vehicletracking_backend.auth.repository.UserAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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

    private UsernamePasswordAuthenticationToken authenticationFor(long id) {
        UserAccountEntity account = new UserAccountEntity("admin", "hash", UserRole.ADMIN, null);
        ReflectionTestUtils.setField(account, "id", id);
        var principal = SecurityConfig.UserAccountPrincipal.from(account);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }
}
