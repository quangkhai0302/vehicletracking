package com.quangkhai.vehicletracking_backend.auth.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Explicit opt-out used by isolated tests/local legacy mode; production defaults to enabled. */
@Configuration
@ConditionalOnProperty(prefix = "auth", name = "security-enabled", havingValue = "false")
public class SecurityDisabledConfig {
    @Bean
    SecurityFilterChain securityDisabledFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
