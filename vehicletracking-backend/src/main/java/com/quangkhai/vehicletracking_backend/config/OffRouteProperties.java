package com.quangkhai.vehicletracking_backend.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "offroute")
public class OffRouteProperties {
    private boolean enabled = true;
    @Positive private double thresholdMeters = 150d;
    @Min(0) private long gracePeriodSeconds = 30;
    @Positive private int consecutiveSamples = 3;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public double getThresholdMeters() { return thresholdMeters; }
    public void setThresholdMeters(double value) { thresholdMeters = value; }
    public long getGracePeriodSeconds() { return gracePeriodSeconds; }
    public void setGracePeriodSeconds(long value) { gracePeriodSeconds = value; }
    public int getConsecutiveSamples() { return consecutiveSamples; }
    public void setConsecutiveSamples(int value) { consecutiveSamples = value; }
}
