package com.quangkhai.vehicletracking_backend.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "here.traffic")
public class HereTrafficProperties {

    @NotBlank
    private String baseUrl = "https://data.traffic.hereapi.com";

    @NotBlank
    private String apiKey;

    @Positive
    private int connectTimeoutMs = 2000;

    @Positive
    private int readTimeoutMs = 5000;

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

    @Override
    public String toString() {
        return "HereTrafficProperties{" +
                "baseUrl='" + baseUrl + '\'' +
                ", apiKey='[REDACTED]'" +
                ", connectTimeoutMs=" + connectTimeoutMs +
                ", readTimeoutMs=" + readTimeoutMs +
                '}';
    }
}
