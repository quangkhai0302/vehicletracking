package com.quangkhai.vehicletracking_backend.route.config;

import com.quangkhai.vehicletracking_backend.route.provider.HereRoutingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class RouteConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @EnableConfigurationProperties(HereRoutingProperties.class)
    static class TestConfig {}

    @Test
    void routingDisabled_withBlankKey_startsSuccessfully() {
        contextRunner.withPropertyValues(
                "here.routing.enabled=false",
                "here.routing.api-key="
        ).run(context -> {
            assertThat(context).hasNotFailed();
            HereRoutingProperties props = context.getBean(HereRoutingProperties.class);
            assertThat(props.isEnabled()).isFalse();
        });
    }

    @Test
    void routingEnabled_withValidKey_startsSuccessfully() {
        contextRunner.withPropertyValues(
                "here.routing.enabled=true",
                "here.routing.api-key=valid-key-xyz"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            HereRoutingProperties props = context.getBean(HereRoutingProperties.class);
            assertThat(props.isEnabled()).isTrue();
            assertThat(props.getApiKey()).isEqualTo("valid-key-xyz");
        });
    }

    @Test
    void routingEnabled_withBlankKey_failsStartupFast() {
        contextRunner.withPropertyValues(
                "here.routing.enabled=true",
                "here.routing.api-key="
        ).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("HERE API key không được để trống khi here.routing.enabled=true");
        });
    }
}
