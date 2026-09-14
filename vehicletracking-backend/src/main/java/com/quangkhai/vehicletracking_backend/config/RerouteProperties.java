package com.quangkhai.vehicletracking_backend.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "reroute")
public class RerouteProperties {
    @Positive private long delaySeconds = 600;
    @Positive private int delayPercent = 30;
    @Positive private long cooldownSeconds = 300;
    @Positive private int consecutiveFetches = 2;
    public long getDelaySeconds() { return delaySeconds; }
    public void setDelaySeconds(long value) { delaySeconds = value; }
    public int getDelayPercent() { return delayPercent; }
    public void setDelayPercent(int value) { delayPercent = value; }
    public long getCooldownSeconds() { return cooldownSeconds; }
    public void setCooldownSeconds(long value) { cooldownSeconds = value; }
    public int getConsecutiveFetches() { return consecutiveFetches; }
    public void setConsecutiveFetches(int value) { consecutiveFetches = value; }
}
