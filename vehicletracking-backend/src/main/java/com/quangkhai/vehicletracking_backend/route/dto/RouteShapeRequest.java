package com.quangkhai.vehicletracking_backend.route.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import com.quangkhai.vehicletracking_backend.route.entity.RoutingProviderName;
public record RouteShapeRequest(@NotNull @Size(max=20) List<@NotNull @Valid ShapePoint> points,
                                RoutingProviderName targetProvider) {
    public RouteShapeRequest(List<ShapePoint> points) { this(points, null); }
    public record ShapePoint(@Min(2) int destinationStopSequence,
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude) {}
}
