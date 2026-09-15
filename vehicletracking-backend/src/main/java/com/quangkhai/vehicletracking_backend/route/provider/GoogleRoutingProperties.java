package com.quangkhai.vehicletracking_backend.route.provider;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "google.routes")
public class GoogleRoutingProperties {
    private boolean enabled;
    @NotBlank
    private String baseUrl = "https://routes.googleapis.com";
    private String apiKey = "";
    @Positive
    private int connectTimeoutMs = 2000;
    @Positive
    private int readTimeoutMs = 10000;
    @Positive
    private int refreshSeconds = 60;
    @Positive
    private int maxConcurrentRequests = 4;
    @Positive
    private int maxRequestsPerMinute = 60;
    @Positive
    private int maxRequestsPerDay = 1000;

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
    public int getRefreshSeconds() { return refreshSeconds; }
    public void setRefreshSeconds(int refreshSeconds) { this.refreshSeconds = refreshSeconds; }
    public int getMaxConcurrentRequests() { return maxConcurrentRequests; }
    public void setMaxConcurrentRequests(int maxConcurrentRequests) { this.maxConcurrentRequests = maxConcurrentRequests; }
    public int getMaxRequestsPerMinute() { return maxRequestsPerMinute; }
    public void setMaxRequestsPerMinute(int maxRequestsPerMinute) { this.maxRequestsPerMinute = maxRequestsPerMinute; }
    public int getMaxRequestsPerDay() { return maxRequestsPerDay; }
    public void setMaxRequestsPerDay(int maxRequestsPerDay) { this.maxRequestsPerDay = maxRequestsPerDay; }

    @AssertTrue(message = "GOOGLE_ROUTES_API_KEY không được để trống khi google.routes.enabled=true")
    public boolean isApiKeyValidWhenEnabled() {
        return !enabled || (apiKey != null && !apiKey.isBlank());
    }

    @Override
    public String toString() {
        return "GoogleRoutingProperties{" + "enabled=" + enabled + ", baseUrl='" + baseUrl + '\''
                + ", apiKey='[REDACTED]', connectTimeoutMs=" + connectTimeoutMs
                + ", readTimeoutMs=" + readTimeoutMs + ", refreshSeconds=" + refreshSeconds
                + ", maxConcurrentRequests=" + maxConcurrentRequests
                + ", maxRequestsPerMinute=" + maxRequestsPerMinute
                + ", maxRequestsPerDay=" + maxRequestsPerDay + '}';
    }
}
