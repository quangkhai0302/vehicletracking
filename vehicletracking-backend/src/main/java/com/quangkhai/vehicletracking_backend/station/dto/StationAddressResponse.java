package com.quangkhai.vehicletracking_backend.station.dto;

public record StationAddressResponse(
    String address,
    Double distanceMeters
) {
}
