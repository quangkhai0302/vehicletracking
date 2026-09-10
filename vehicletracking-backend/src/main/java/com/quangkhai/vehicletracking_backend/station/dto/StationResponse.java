package com.quangkhai.vehicletracking_backend.station.dto;

import com.quangkhai.vehicletracking_backend.station.entity.StationEntity;

import java.math.BigDecimal;
import java.time.Instant;

public record StationResponse(
        Long id,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer checkinRadiusMeters,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static StationResponse from(StationEntity station) {
        return new StationResponse(
                station.getId(),
                station.getName(),
                station.getAddress(),
                station.getLatitude(),
                station.getLongitude(),
                station.getCheckinRadiusMeters(),
                station.isActive(),
                station.getCreatedAt(),
                station.getUpdatedAt()
        );
    }
}
