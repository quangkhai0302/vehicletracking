package com.quangkhai.vehicletracking_backend.traffic.eta;

import com.quangkhai.vehicletracking_backend.route.entity.RouteTransportMode;
import com.quangkhai.vehicletracking_backend.route.entity.RoutingProviderName;
import com.quangkhai.vehicletracking_backend.route.provider.CalculatedRoute;
import com.quangkhai.vehicletracking_backend.route.provider.CalculatedSection;
import com.quangkhai.vehicletracking_backend.route.provider.GoogleRoutingProperties;
import com.quangkhai.vehicletracking_backend.route.provider.RoutingProviderRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleEtaCoordinatorTest {
    @Test
    void concurrentReadersShareOneProviderRequestAndThenUseTtlCache() throws Exception {
        var providers = mock(RoutingProviderRegistry.class);
        var properties = new GoogleRoutingProperties();
        properties.setRefreshSeconds(60);
        Instant now = Instant.parse("2026-09-15T00:00:00Z");
        var route = new CalculatedRoute(now,
                List.of(new CalculatedSection(1, 2, "geometry", 100, 10, 8)));
        var release = new CountDownLatch(1);
        when(providers.calculate(eq(RoutingProviderName.GOOGLE), any())).thenAnswer(invocation -> {
            release.await();
            return route;
        });
        var coordinator = new GoogleEtaCoordinator(providers, properties);

        try (var executor = Executors.newFixedThreadPool(6)) {
            var futures = new ArrayList<java.util.concurrent.Future<GoogleEtaCoordinator.Snapshot>>();
            for (int index = 0; index < 6; index++) {
                futures.add(executor.submit(() -> coordinator.calculate("trip:1", List.of(), RouteTransportMode.CAR, now)));
            }
            release.countDown();
            for (var future : futures) assertThat(future.get().route()).isSameAs(route);
        }
        assertThat(coordinator.calculate("trip:1", List.of(), RouteTransportMode.CAR, now.plusSeconds(30)).route())
                .isSameAs(route);
        verify(providers, times(1)).calculate(eq(RoutingProviderName.GOOGLE), any());
    }
}
