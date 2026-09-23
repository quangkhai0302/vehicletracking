package com.quangkhai.vehicletracking_backend.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "trip.lifecycle")
public class TripLifecycleProperties {
    @Min(0)
    private long earlyStartWindowSeconds = 1_800;
    @Min(0)
    private long lateStartWindowSeconds = 7_200;

    public long getEarlyStartWindowSeconds() { return earlyStartWindowSeconds; }
    public void setEarlyStartWindowSeconds(long value) { earlyStartWindowSeconds = value; }
    public long getLateStartWindowSeconds() { return lateStartWindowSeconds; }
    public void setLateStartWindowSeconds(long value) { lateStartWindowSeconds = value; }
}
