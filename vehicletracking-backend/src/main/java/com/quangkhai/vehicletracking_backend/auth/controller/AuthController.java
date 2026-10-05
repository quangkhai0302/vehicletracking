package com.quangkhai.vehicletracking_backend.auth.controller;

import com.quangkhai.vehicletracking_backend.auth.config.SecurityConfig.UserAccountPrincipal;
import com.quangkhai.vehicletracking_backend.auth.dto.AuthUserResponse;
import com.quangkhai.vehicletracking_backend.auth.dto.DriverPasswordChangeRequest;
import com.quangkhai.vehicletracking_backend.auth.dto.AdminRegistrationRequest;
import com.quangkhai.vehicletracking_backend.auth.dto.LoginRequest;
import com.quangkhai.vehicletracking_backend.auth.dto.UserAccountResponse;
import com.quangkhai.vehicletracking_backend.auth.service.UserAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "auth", name = "security-enabled", havingValue = "true", matchIfMissing = true)
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContexts;
    private final UserAccountService userAccounts;

    /**
     * Creates the double-submit CSRF cookie before the SPA issues login or other unsafe requests.
     */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/register-admin")
    public ResponseEntity<UserAccountResponse> registerAdmin(@Valid @RequestBody AdminRegistrationRequest input) {
        UserAccountResponse created = userAccounts.registerAdmin(input);
        return ResponseEntity.status(CREATED).body(created);
    }

    @PostMapping("/login")
    public AuthUserResponse login(@Valid @RequestBody LoginRequest input,
                                  HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(input.username(), input.password()));
        } catch (AuthenticationException ex) {
            throw new ResponseStatusException(UNAUTHORIZED, "Tên đăng nhập hoặc mật khẩu không đúng.");
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        // Rotate the session identifier because authentication is performed in the controller,
        // outside Spring Security's form-login session fixation strategy.
        request.getSession(true);
        request.changeSessionId();
        securityContexts.saveContext(context, request, response);
        return AuthUserResponse.from((UserAccountPrincipal) authentication.getPrincipal());
    }

    @GetMapping("/me")
    public AuthUserResponse me(Authentication authentication) {
        return AuthUserResponse.from((UserAccountPrincipal) authentication.getPrincipal());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody DriverPasswordChangeRequest input,
                                               Authentication authentication, HttpServletRequest request,
                                               HttpServletResponse response) {
        UserAccountPrincipal principal = (UserAccountPrincipal) authentication.getPrincipal();
        userAccounts.changeDriverPassword(principal.accountId(), principal.getPassword(), input);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }
}
