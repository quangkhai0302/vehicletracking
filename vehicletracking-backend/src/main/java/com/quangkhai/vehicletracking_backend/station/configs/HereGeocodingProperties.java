package com.quangkhai.vehicletracking_backend.station.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;

@Validated 
@ConfigurationProperties (prefix = "here.geocoding")
public record HereGeocodingProperties(
    boolean enabled,
    String apiKey,
    @Min(1) int connectTimeoutMs,
    @Min(1) int readTimeoutMs
) {
    @AssertTrue(message = "HERE API key không được để trống khi geocoding được bật")

    public boolean isApiKeyConfiguredWhenEnabled() {
        return !enabled || (apiKey != null && !apiKey.isBlank());
    }

    @Override 
    public String toString() {
        return "HereGeocodingProperties[enabled=" + enabled + ", apiKey=****, connectTimeoutMs=" + connectTimeoutMs + ", readTimeoutMs=" + readTimeoutMs + "]";
    }

}
