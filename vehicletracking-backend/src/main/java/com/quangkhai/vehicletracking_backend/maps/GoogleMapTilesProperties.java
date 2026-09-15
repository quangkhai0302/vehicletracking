package com.quangkhai.vehicletracking_backend.maps;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "google.map-tiles")
public class GoogleMapTilesProperties {
    private boolean enabled;
    @NotBlank private String baseUrl = "https://tile.googleapis.com";
    private String apiKey = "";
    @Positive private int connectTimeoutMs = 2000;
    @Positive private int readTimeoutMs = 10000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }

    @AssertTrue(message = "GOOGLE_MAP_TILES_API_KEY không được để trống khi google.map-tiles.enabled=true")
    public boolean isApiKeyValidWhenEnabled() { return !enabled || (apiKey != null && !apiKey.isBlank()); }
}
