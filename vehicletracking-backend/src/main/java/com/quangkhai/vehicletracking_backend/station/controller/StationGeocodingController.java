package com.quangkhai.vehicletracking_backend.station.controller;

import java.math.BigDecimal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.quangkhai.vehicletracking_backend.station.dto.StationAddressResponse;
import com.quangkhai.vehicletracking_backend.station.service.StationGeocodingService;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

@RestController
@RequestMapping("/api/v1/stations")
public class StationGeocodingController {
    
    private final StationGeocodingService service;

    public StationGeocodingController(StationGeocodingService service) {
        this.service = service;
    }

    @GetMapping ("/reverse-geocode")
    public StationAddressResponse reverseGeocode(
        @RequestParam("latitude")
        @DecimalMin("-90")
        @DecimalMax("90")
        BigDecimal latitude,

        @RequestParam("longitude")
        @DecimalMin("-180")
        @DecimalMax("180")
        BigDecimal longitude
    ) {
        return service.reverseGeocode(latitude, longitude);
    }
}
