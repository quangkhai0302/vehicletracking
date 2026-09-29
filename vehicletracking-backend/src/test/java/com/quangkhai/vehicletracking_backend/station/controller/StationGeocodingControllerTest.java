package com.quangkhai.vehicletracking_backend.station.controller;

import com.quangkhai.vehicletracking_backend.config.CorsProperties;
import com.quangkhai.vehicletracking_backend.station.dto.StationAddressResponse;
import com.quangkhai.vehicletracking_backend.station.service.StationGeocodingService;
import com.quangkhai.vehicletracking_backend.station.service.StationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {StationGeocodingController.class, StationController.class}, properties = {
        "app.cors.allowed-origins=http://localhost:5173"
})
@EnableConfigurationProperties(CorsProperties.class)
class StationGeocodingControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean StationGeocodingService geocoding;
    @MockitoBean StationService stations;

    @Test
    void reverseGeocodeRouteTakesPrecedenceOverStationIdAndReturnsAddress() throws Exception {
        when(geocoding.reverseGeocode(any(), any())).thenReturn(new StationAddressResponse("Địa chỉ mẫu", 12.5));
        mvc.perform(get("/api/v1/stations/reverse-geocode").param("latitude", "10.8").param("longitude", "106.7"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.address").value("Địa chỉ mẫu"))
                .andExpect(jsonPath("$.distanceMeters").value(12.5));
        verify(geocoding).reverseGeocode(new BigDecimal("10.8"), new BigDecimal("106.7"));
        verifyNoInteractions(stations);
    }

    @ParameterizedTest
    @CsvSource({"0,0", "-90,-180", "90,180"})
    void acceptsInclusiveCoordinateBounds(String latitude, String longitude) throws Exception {
        when(geocoding.reverseGeocode(any(), any())).thenReturn(new StationAddressResponse(null, null));
        mvc.perform(get("/api/v1/stations/reverse-geocode").param("latitude", latitude).param("longitude", longitude))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"address\":null,\"distanceMeters\":null}"));
        verify(geocoding).reverseGeocode(new BigDecimal(latitude), new BigDecimal(longitude));
    }

    @ParameterizedTest
    @CsvSource({"91,106", "-91,106", "10,181", "10,-181", "abc,106", "10,NaN", "Infinity,106"})
    void invalidCoordinatesReturn400WithoutProviderCall(String latitude, String longitude) throws Exception {
        mvc.perform(get("/api/v1/stations/reverse-geocode").param("latitude", latitude).param("longitude", longitude))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(geocoding, stations);
    }

    @Test
    void missingCoordinatesReturn400() throws Exception {
        mvc.perform(get("/api/v1/stations/reverse-geocode").param("latitude", "10.8"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/stations/reverse-geocode").param("longitude", "106.7"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(geocoding);
    }

    @Test
    void serviceUnavailableReturnsProblemDetailForManualFallback() throws Exception {
        when(geocoding.reverseGeocode(any(), any())).thenThrow(new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Bạn có thể nhập thủ công."));
        mvc.perform(get("/api/v1/stations/reverse-geocode").param("latitude", "10.8").param("longitude", "106.7"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Bạn có thể nhập thủ công."));
    }
}
