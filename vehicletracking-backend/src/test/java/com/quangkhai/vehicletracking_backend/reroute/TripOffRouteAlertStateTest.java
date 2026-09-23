package com.quangkhai.vehicletracking_backend.reroute;

import com.quangkhai.vehicletracking_backend.reroute.entity.TripOffRouteAlertStateEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TripOffRouteAlertStateTest {
    private static final Instant START = Instant.parse("2026-01-01T08:00:00Z");

    @Test
    void accumulatesAStableBreachAndRearmsAfterReturningToTheRoute() {
        var state = new TripOffRouteAlertStateEntity(null, 1, START);

        state.observeBreach(220, START.plusSeconds(10), START.plusSeconds(10));
        state.observeBreach(240, START.plusSeconds(20), START.plusSeconds(20));
        state.markActive(START.plusSeconds(20));

        assertThat(state.getConsecutiveBreachCount()).isEqualTo(2);
        assertThat(state.isActive()).isTrue();
        assertThat(state.getEpisode()).isEqualTo(1);
        assertThat(state.getLastDistanceMeters()).isEqualTo(240);

        state.clear(START.plusSeconds(30), START.plusSeconds(30));

        assertThat(state.isActive()).isFalse();
        assertThat(state.getConsecutiveBreachCount()).isZero();
        assertThat(state.getBreachStartedAt()).isNull();
        assertThat(state.getLastRecordedAt()).isEqualTo(START.plusSeconds(30));
        assertThat(state.getEpisode()).isEqualTo(1);
    }

    @Test
    void resetsAllDetectorStateWhenTripAttemptChanges() {
        var state = new TripOffRouteAlertStateEntity(null, 1, START);
        state.observeBreach(250, START.plusSeconds(5), START.plusSeconds(5));
        state.markActive(START.plusSeconds(5));

        state.resetForAttempt(2, START.plusSeconds(60));

        assertThat(state.getAttemptNumber()).isEqualTo(2);
        assertThat(state.isActive()).isFalse();
        assertThat(state.getEpisode()).isZero();
        assertThat(state.getConsecutiveBreachCount()).isZero();
        assertThat(state.getLastDistanceMeters()).isNull();
        assertThat(state.getLastRecordedAt()).isNull();
    }
}
