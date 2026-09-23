package com.quangkhai.vehicletracking_backend.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "reporting")
public class ReportingProperties {
    @Positive
    private double defaultSpeedLimitKmh = 80d;
    @Positive
    private int maxRangeDays = 366;

    public double getDefaultSpeedLimitKmh() {
        return defaultSpeedLimitKmh;
    }

    public void setDefaultSpeedLimitKmh(double value) {
        defaultSpeedLimitKmh = value;
    }

    public int getMaxRangeDays() {
        return maxRangeDays;
    }

    public void setMaxRangeDays(int value) {
        maxRangeDays = value;
    }
}
