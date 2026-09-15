package com.quangkhai.vehicletracking_backend.route.provider;

import com.quangkhai.vehicletracking_backend.route.error.RouteOperationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleRoutingRequestGuardTest {
    @Test
    void rejectsRequestsBeyondMinuteBudgetBeforeCallingSupplier() {
        var properties = new GoogleRoutingProperties();
        properties.setMaxRequestsPerMinute(1);
        properties.setMaxRequestsPerDay(10);
        var guard = new GoogleRoutingRequestGuard(properties,
                Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), ZoneOffset.UTC));

        assertThat(guard.execute(() -> "first")).isEqualTo("first");
        assertThatThrownBy(() -> guard.execute(() -> "must not run"))
                .isInstanceOf(RouteOperationException.class)
                .hasMessageContaining("mỗi phút");
    }

    @Test
    void rejectsRequestsBeyondDailyBudget() {
        var properties = new GoogleRoutingProperties();
        properties.setMaxRequestsPerMinute(10);
        properties.setMaxRequestsPerDay(1);
        var guard = new GoogleRoutingRequestGuard(properties,
                Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), ZoneOffset.UTC));

        guard.execute(() -> null);
        assertThatThrownBy(() -> guard.execute(() -> null))
                .isInstanceOf(RouteOperationException.class)
                .hasMessageContaining("trong ngày");
    }
}
