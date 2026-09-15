package com.quangkhai.vehicletracking_backend.route.provider;

import com.quangkhai.vehicletracking_backend.route.entity.RouteTransportMode;
import com.quangkhai.vehicletracking_backend.route.entity.RoutingProviderName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingProviderRegistryTest {
    @Test
    void dispatchesByPinnedProviderInsteadOfDefaultProvider() {
        var properties = new RoutingProviderProperties();
        properties.setProvider(RoutingProviderName.GOOGLE);
        RoutingProvider here = provider(RoutingProviderName.HERE, "here");
        RoutingProvider google = provider(RoutingProviderName.GOOGLE, "google");
        var registry = new RoutingProviderRegistry(List.of(here, google), properties);

        var request = new RoutingRequest(List.of(), RouteTransportMode.CAR, Instant.EPOCH, false);
        assertThat(registry.defaultProvider()).isEqualTo(RoutingProviderName.GOOGLE);
        assertThat(registry.calculate(RoutingProviderName.HERE, request).sections().getFirst().encodedPolyline())
                .isEqualTo("here");
    }

    private RoutingProvider provider(RoutingProviderName name, String geometry) {
        return new RoutingProvider() {
            @Override public RoutingProviderName name() { return name; }
            @Override public CalculatedRoute calculate(RoutingRequest request) {
                return new CalculatedRoute(Instant.EPOCH,
                        List.of(new CalculatedSection(1, 2, geometry, 1, 1, 1)));
            }
        };
    }
}
