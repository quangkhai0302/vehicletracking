package com.quangkhai.vehicletracking_backend.route.provider;

import com.quangkhai.vehicletracking_backend.route.entity.RoutingProviderName;
import com.quangkhai.vehicletracking_backend.route.error.RouteErrorCode;
import com.quangkhai.vehicletracking_backend.route.error.RouteOperationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class RoutingProviderRegistry {
    private final Map<RoutingProviderName, RoutingProvider> providers;
    private final RoutingProviderProperties properties;

    public RoutingProviderRegistry(List<RoutingProvider> providers, RoutingProviderProperties properties) {
        this.properties = properties;
        Map<RoutingProviderName, RoutingProvider> indexed = new EnumMap<>(RoutingProviderName.class);
        for (RoutingProvider provider : providers) {
            if (indexed.put(provider.name(), provider) != null) {
                throw new IllegalStateException("Duplicate routing provider: " + provider.name());
            }
        }
        this.providers = Map.copyOf(indexed);
    }

    public RoutingProviderName defaultProvider() {
        return properties.getProvider();
    }

    public CalculatedRoute calculate(RoutingProviderName providerName, RoutingRequest request) {
        RoutingProvider provider = providers.get(providerName);
        if (provider == null) {
            throw new RouteOperationException(HttpStatus.SERVICE_UNAVAILABLE, RouteErrorCode.ROUTING_UNAVAILABLE,
                    "Routing provider " + providerName + " is not available");
        }
        return provider.calculate(request);
    }
}
