package com.quangkhai.vehicletracking_backend.auth.config;

import com.quangkhai.vehicletracking_backend.auth.entity.UserAccountEntity;
import com.quangkhai.vehicletracking_backend.auth.repository.UserAccountRepository;
import com.quangkhai.vehicletracking_backend.config.CorsProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import java.util.List;

@Configuration
@EnableWebSecurity
@ConditionalOnProperty(prefix = "auth", name = "security-enabled", havingValue = "true", matchIfMissing = true)
public class SecurityConfig {
    @Bean
    UserDetailsService userDetailsService(UserAccountRepository accounts) {
        return username -> accounts.findByUsername(username.trim().toLowerCase())
                .map(UserAccountPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản."));
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new org.springframework.security.authentication.ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }

    /**
     * The validation filter is inserted into Spring Security's chain below. Disable
     * Boot's automatic servlet registration so it cannot run twice (once before the
     * SecurityContext is restored and once inside the chain).
     */
    @Bean
    FilterRegistrationBean<SessionAccountValidationFilter> sessionAccountValidationFilterRegistration(
            SessionAccountValidationFilter filter) {
        FilterRegistrationBean<SessionAccountValidationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        // The SPA reads this non-HttpOnly token cookie and mirrors it in X-XSRF-TOKEN.
        // The authentication cookie remains HttpOnly (server.servlet.session.cookie.http-only).
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Lax"));
        return repository;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contexts,
                                            CsrfTokenRepository csrfTokens,
                                            SessionAccountValidationFilter sessionAccountValidationFilter) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokens)
                        // The SPA sends the raw value from the XSRF-TOKEN cookie. Do not
                        // replace it with Spring Security's BREACH-masked request value.
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .cors(Customizer.withDefaults())
                .securityContext(context -> context.securityContextRepository(contexts))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .addFilterAfter(sessionAccountValidationFilter, SecurityContextHolderFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/auth/csrf").permitAll()
                        .requestMatchers("/api/v1/auth/register-admin").permitAll()
                        .requestMatchers("/api/v1/auth/change-password").hasRole("DRIVER")
                        .requestMatchers("/api/v1/auth/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/traffic/flow", "/api/v1/traffic/incidents").hasAnyRole("ADMIN", "DRIVER")
                        .requestMatchers("/api/v1/driver/**").hasRole("DRIVER")
                        .requestMatchers("/api/v1/users/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) -> response.sendError(HttpServletResponse.SC_FORBIDDEN)));
        return http.build();
    }

    public static final class UserAccountPrincipal implements org.springframework.security.core.userdetails.UserDetails {
        private final long accountId;
        private final String username;
        private final String passwordHash;
        private final com.quangkhai.vehicletracking_backend.auth.entity.UserRole role;
        private final Long driverId;
        private final String driverName;
        private final boolean active;
        private final boolean passwordChangeRequired;

        private UserAccountPrincipal(long accountId, String username, String passwordHash,
                                     com.quangkhai.vehicletracking_backend.auth.entity.UserRole role,
                                     Long driverId, String driverName, boolean active, boolean passwordChangeRequired) {
            this.accountId = accountId; this.username = username; this.passwordHash = passwordHash;
            this.role = role; this.driverId = driverId; this.driverName = driverName; this.active = active;
            this.passwordChangeRequired = passwordChangeRequired;
        }

        static UserAccountPrincipal from(UserAccountEntity account) {
            boolean active = account.isActive() && (account.getDriver() == null || account.getDriver().isActive());
            return new UserAccountPrincipal(account.getId(), account.getUsername(), account.getPasswordHash(),
                    account.getRole(), account.getDriver() == null ? null : account.getDriver().getId(),
                    account.getDriver() == null ? null : account.getDriver().getFullName(), active,
                    account.isPasswordChangeRequired());
        }

        public long accountId() { return accountId; }
        public String usernameValue() { return username; }
        public com.quangkhai.vehicletracking_backend.auth.entity.UserRole role() { return role; }
        public Long driverId() { return driverId; }
        public String driverName() { return driverName; }
        public boolean passwordChangeRequired() { return passwordChangeRequired; }
        @Override public String getUsername() { return username; }
        @Override public String getPassword() { return passwordHash; }
        @Override public java.util.Collection<org.springframework.security.core.GrantedAuthority> getAuthorities() {
            return List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role.name()));
        }
        @Override public boolean isAccountNonExpired() { return true; }
        @Override public boolean isAccountNonLocked() { return active; }
        @Override public boolean isCredentialsNonExpired() { return true; }
        @Override public boolean isEnabled() { return active; }
    }
}
