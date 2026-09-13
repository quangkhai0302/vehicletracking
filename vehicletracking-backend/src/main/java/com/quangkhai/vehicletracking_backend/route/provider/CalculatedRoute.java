package com.quangkhai.vehicletracking_backend.route.provider;

import java.time.Instant;
import java.util.List;

public record CalculatedRoute(
        Instant estimatedDepartureAt,
        List<CalculatedSection> sections
) {
}
