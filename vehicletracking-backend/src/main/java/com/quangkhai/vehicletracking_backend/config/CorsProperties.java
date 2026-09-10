package com.quangkhai.vehicletracking_backend.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    @NotEmpty(message = "Allowed origins list must not be empty")
    private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173", "http://127.0.0.1:5173"));

    @AssertTrue(message = "Allowed origins must contain valid HTTP/HTTPS URLs without path/query and cannot contain wildcard '*'")
    public boolean isValidOrigins() {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            return false;
        }
        for (String origin : allowedOrigins) {
            if (origin == null || origin.isBlank()) {
                return false;
            }
            String trimmed = origin.trim();
            if (trimmed.equals("*") || trimmed.contains("*")) {
                return false;
            }
            try {
                URI uri = URI.create(trimmed);
                String scheme = uri.getScheme();
                if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                    return false;
                }
                if (uri.getHost() == null || uri.getHost().isBlank()) {
                    return false;
                }
                if (uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !uri.getRawPath().equals("/")) {
                    return false;
                }
                if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
