package com.quangkhai.vehicletracking_backend.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "here.traffic")
public class HereTrafficProperties {

    private boolean enabled = false;

    @NotBlank
    private String baseUrl = "https://data.traffic.hereapi.com";

    @NotBlank
    private String tileBaseUrl = "https://traffic.maps.hereapi.com";

    @NotBlank
    private String mapTileBaseUrl = "https://maps.hereapi.com";

    @NotBlank
    private String vectorStyleBaseUrl = "https://assets.vector.hereapi.com/styles/berlin/base/mapbox/tilezen";

    private String apiKey = "";

    @Positive
    private int connectTimeoutMs = 2000;

    @Positive
    private int readTimeoutMs = 5000;

    @PositiveOrZero
    private long cacheTtlSeconds = 60;

    @PositiveOrZero
    private long staleTtlSeconds = 300;

    @Positive
    private int corridorRadiusMeters = 100;

    @Positive
    private int maxBboxSpanHundredths = 100;

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

    public String getTileBaseUrl() {
        return tileBaseUrl;
    }

    public void setTileBaseUrl(String tileBaseUrl) {
        this.tileBaseUrl = tileBaseUrl;
    }

    public String getMapTileBaseUrl() {
        return mapTileBaseUrl;
    }

    public void setMapTileBaseUrl(String mapTileBaseUrl) {
        this.mapTileBaseUrl = mapTileBaseUrl;
    }

    public String getVectorStyleBaseUrl() {
        return vectorStyleBaseUrl;
    }

    public void setVectorStyleBaseUrl(String vectorStyleBaseUrl) {
        this.vectorStyleBaseUrl = vectorStyleBaseUrl;
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

    public long getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    public void setCacheTtlSeconds(long cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public long getStaleTtlSeconds() {
        return staleTtlSeconds;
    }

    public void setStaleTtlSeconds(long staleTtlSeconds) {
        this.staleTtlSeconds = staleTtlSeconds;
    }

    public int getCorridorRadiusMeters() {
        return corridorRadiusMeters;
    }

    public void setCorridorRadiusMeters(int corridorRadiusMeters) {
        this.corridorRadiusMeters = corridorRadiusMeters;
    }

    public int getMaxBboxSpanHundredths() {
        return maxBboxSpanHundredths;
    }

    public void setMaxBboxSpanHundredths(int maxBboxSpanHundredths) {
        this.maxBboxSpanHundredths = maxBboxSpanHundredths;
    }

    public String getApiBaseUrl() {
        String value = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        if (value.endsWith("/v7")) return value;
        return value + "/v7";
    }

    public String getTileApiBaseUrl() {
        return tileBaseUrl == null ? "" : tileBaseUrl.trim().replaceAll("/+$", "");
    }

    public String getMapTileApiBaseUrl() {
        return mapTileBaseUrl == null ? "" : mapTileBaseUrl.trim().replaceAll("/+$", "");
    }

    public String getVectorStyleApiBaseUrl() {
        return vectorStyleBaseUrl == null ? "" : vectorStyleBaseUrl.trim().replaceAll("/+$", "");
    }

    @AssertTrue(message = "HERE API key không được để trống khi here.traffic.enabled=true")
    public boolean isApiKeyValidWhenEnabled() {
        return !enabled || (apiKey != null && !apiKey.trim().isEmpty());
    }

    @Override
    public String toString() {
        return "HereTrafficProperties{" +
                "baseUrl='" + baseUrl + '\'' +
                ", tileBaseUrl='" + tileBaseUrl + '\'' +
                ", mapTileBaseUrl='" + mapTileBaseUrl + '\'' +
                ", vectorStyleBaseUrl='" + vectorStyleBaseUrl + '\'' +
                ", apiKey='[REDACTED]'" +
                ", connectTimeoutMs=" + connectTimeoutMs +
                ", readTimeoutMs=" + readTimeoutMs +
                ", cacheTtlSeconds=" + cacheTtlSeconds +
                ", staleTtlSeconds=" + staleTtlSeconds +
                ", corridorRadiusMeters=" + corridorRadiusMeters +
                '}';
    }
}
