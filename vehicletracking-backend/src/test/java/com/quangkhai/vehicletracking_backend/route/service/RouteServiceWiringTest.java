package com.quangkhai.vehicletracking_backend.route.service;

import com.quangkhai.vehicletracking_backend.route.controller.RouteController;
import com.quangkhai.vehicletracking_backend.route.provider.RoutingProviderRegistry;
import com.quangkhai.vehicletracking_backend.route.repository.RouteRepository;
import com.quangkhai.vehicletracking_backend.station.repository.StationRepository;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

class RouteServiceWiringTest {
    @Test
    void springCreatesControllerAndServiceWithAllRequiredDependencies() {
        var trips = repositoryStub(TripRepository.class);
        new ApplicationContextRunner()
                .withBean(RouteRepository.class, () -> repositoryStub(RouteRepository.class))
                .withBean(StationRepository.class, () -> repositoryStub(StationRepository.class))
                .withBean(TripRepository.class, () -> trips)
                .withBean(RoutingProviderRegistry.class, () -> org.mockito.Mockito.mock(RoutingProviderRegistry.class))
                .withUserConfiguration(RoutePersistenceService.class, RouteService.class, RouteController.class)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(RouteController.class).hasSingleBean(RouteService.class);
                    assertThat(ReflectionTestUtils.getField(context.getBean(RouteService.class), "tripRepository"))
                            .isSameAs(trips);
                });
    }

    // No database or JVM agent is needed to verify constructor injection.
    private static <T> T repositoryStub(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "toString" -> type.getSimpleName();
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new AssertionError("Startup must not query repository: " + method.getName());
            };
        }));
    }
}
