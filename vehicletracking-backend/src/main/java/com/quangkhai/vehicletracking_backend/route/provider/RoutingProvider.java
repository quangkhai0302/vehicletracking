package com.quangkhai.vehicletracking_backend.route.provider;

import com.quangkhai.vehicletracking_backend.route.entity.RoutingProviderName;

public interface RoutingProvider {

    RoutingProviderName name();

    CalculatedRoute calculate(RoutingRequest request);
}
