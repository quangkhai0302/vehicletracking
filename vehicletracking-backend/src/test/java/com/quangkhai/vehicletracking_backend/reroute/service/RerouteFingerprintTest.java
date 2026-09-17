package com.quangkhai.vehicletracking_backend.reroute.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RerouteFingerprintTest {
    @Test
    void preservesExistingShortKeys() {
        assertThat(RerouteEvaluationService.boundedFingerprint("DELAY:route")).isEqualTo("DELAY:route");
        assertThat(RerouteEvaluationService.boundedFingerprint("x".repeat(200))).hasSize(200);
    }

    @Test
    void longKeysFitBothStateAndNotificationColumnsWithoutTruncatingIdentity() {
        String input = "DELAY:" + "FLOW:long-provider-id:2|".repeat(100);
        String key = RerouteEvaluationService.boundedFingerprint(input);
        assertThat(key).startsWith("sha256:").hasSize(71);
        assertThat(key).isEqualTo(RerouteEvaluationService.boundedFingerprint(input));
        assertThat(key).isNotEqualTo(RerouteEvaluationService.boundedFingerprint(input + "other"));
        assertThat(("REROUTE_UNAVAILABLE:" + Long.MAX_VALUE + ":" + key).length()).isLessThan(255);
        assertThat(RerouteEvaluationService.boundedFingerprint("x".repeat(201))).hasSize(71);
    }
}
