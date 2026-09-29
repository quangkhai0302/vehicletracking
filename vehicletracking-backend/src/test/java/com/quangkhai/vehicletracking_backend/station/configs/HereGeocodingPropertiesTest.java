package com.quangkhai.vehicletracking_backend.station.configs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class HereGeocodingPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class)
            .withPropertyValues("here.geocoding.connect-timeout-ms=2000", "here.geocoding.read-timeout-ms=5000");

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(HereGeocodingProperties.class)
    static class PropertiesConfiguration {}

    @Test
    void disabledAllowsMissingKeyAndMasksToString() {
        runner.withPropertyValues("here.geocoding.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(HereGeocodingProperties.class).enabled()).isFalse();
        });
        assertThat(new HereGeocodingProperties(true, "fixture-key-not-real", 2000, 5000).toString())
                .contains("apiKey=****").doesNotContain("fixture-key-not-real");
    }

    @Test
    void enabledRequiresKeyAtStartup() {
        runner.withPropertyValues("here.geocoding.enabled=true").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("here.geocoding.enabled=true", "here.geocoding.api-key= ")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("here.geocoding.enabled=true", "here.geocoding.api-key=fixture-key-not-real")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"here.geocoding.connect-timeout-ms=0", "here.geocoding.read-timeout-ms=0",
            "here.geocoding.connect-timeout-ms=-1", "here.geocoding.read-timeout-ms=-1"})
    void timeoutsMustBePositive(String property) {
        runner.withPropertyValues("here.geocoding.enabled=false", property)
                .run(context -> assertThat(context).hasFailed());
    }
}
