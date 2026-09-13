package com.quangkhai.vehicletracking_backend.route.provider;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "here.routing")
public class HereRoutingProperties {

    private boolean enabled = false;

    @NotBlank
    private String baseUrl = "https://router.hereapi.com";

    private String apiKey = "";

    @Positive
    private int connectTimeoutMs = 2000;

    @Positive
    private int readTimeoutMs = 10000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    @AssertTrue(message = "HERE API key không được để trống khi here.routing.enabled=true")
    public boolean isApiKeyValidWhenEnabled() {
        return !enabled || (apiKey != null && !apiKey.trim().isEmpty());
    }

    @Override
    public String toString() {
        return "HereRoutingProperties{" +
                "enabled=" + enabled +
                ", baseUrl='" + baseUrl + '\'' +
                ", apiKey='[REDACTED]'" +
                ", connectTimeoutMs=" + connectTimeoutMs +
                ", readTimeoutMs=" + readTimeoutMs +
                '}';
    }
}
