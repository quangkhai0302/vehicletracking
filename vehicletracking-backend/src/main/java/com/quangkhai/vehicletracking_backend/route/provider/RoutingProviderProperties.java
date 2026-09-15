package com.quangkhai.vehicletracking_backend.route.provider;

import com.quangkhai.vehicletracking_backend.route.entity.RoutingProviderName;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "routing")
public class RoutingProviderProperties {
    private RoutingProviderName provider = RoutingProviderName.HERE;

    public RoutingProviderName getProvider() {
        return provider;
    }

    public void setProvider(RoutingProviderName provider) {
        this.provider = provider == null ? RoutingProviderName.HERE : provider;
    }
}
