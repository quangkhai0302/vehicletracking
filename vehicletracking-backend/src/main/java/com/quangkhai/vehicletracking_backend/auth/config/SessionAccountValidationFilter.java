package com.quangkhai.vehicletracking_backend.auth.config;

import com.quangkhai.vehicletracking_backend.auth.repository.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Invalidates a stale session when its account/driver is disabled or its
 * password hash no longer matches the persisted credential.
 */
@Component
public class SessionAccountValidationFilter extends OncePerRequestFilter {
    private final UserAccountRepository accounts;

    @Autowired
    public SessionAccountValidationFilter(ObjectProvider<UserAccountRepository> accounts) {
        this.accounts = accounts.getIfAvailable();
    }

    /** Constructor kept explicit for focused filter unit tests. */
    public SessionAccountValidationFilter(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (accounts != null && authentication != null && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getPrincipal() instanceof SecurityConfig.UserAccountPrincipal principal
                && !accounts.isActiveForAuthentication(principal.accountId(), principal.getPassword())) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
