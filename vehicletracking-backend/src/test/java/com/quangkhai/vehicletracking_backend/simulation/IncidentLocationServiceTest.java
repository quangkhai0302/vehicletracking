package com.quangkhai.vehicletracking_backend.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.quangkhai.vehicletracking_backend.simulation.service.IncidentLocationService;
import com.quangkhai.vehicletracking_backend.simulation.motion.RouteMotion.Frame;
import com.quangkhai.vehicletracking_backend.station.dto.StationAddressResponse;
import com.quangkhai.vehicletracking_backend.station.service.StationGeocodingService;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStopEntity;

class IncidentLocationServiceTest {
    final StationGeocodingService geocoding = mock(StationGeocodingService.class);
    final IncidentLocationService service = new IncidentLocationService(geocoding);
    final Frame between = new Frame(10.775, 106.705, 0, 10, 50, 2, 10, 0, false, false);

    @Test void resolvesTheCapturedFrameAndKeepsVietnameseAddress() {
        when(geocoding.reverseGeocode(BigDecimal.valueOf(10.775), BigDecimal.valueOf(106.705)))
            .thenReturn(new StationAddressResponse("  Đường Kinh Dương Vương, Bình Tân, TP.HCM  ", 3d));
        assertThat(service.locate(mock(TripEntity.class), between)).isEqualTo("Đường Kinh Dương Vương, Bình Tân, TP.HCM");
    }
    @Test void unavailableProviderFallsBackToCurrentRouteSegment() {
        when(geocoding.reverseGeocode(any(), any())).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(service.locate(trip(), between)).isEqualTo("Trên đường từ Bến xe đến Trường học");
    }
    @Test void noAddressFallsBackToStationWhenInsideCheckInRadius() {
        when(geocoding.reverseGeocode(any(), any())).thenReturn(new StationAddressResponse(null, null));
        var atStop = new Frame(10.77, 106.7, 0, 0, 0, 2, 10, 0, true, false);
        assertThat(service.locate(trip(), atStop)).isEqualTo("Tại Bến xe");
    }
    @Test void labelFitsDatabaseAndDoesNotBlockReportingWhenStopsAreMissing() {
        when(geocoding.reverseGeocode(any(), any())).thenReturn(new StationAddressResponse("a".repeat(600), null));
        assertThat(service.locate(mock(TripEntity.class), between)).hasSize(500);
        when(geocoding.reverseGeocode(any(), any())).thenReturn(null);
        var trip = mock(TripEntity.class); when(trip.getStops()).thenReturn(List.of());
        assertThat(service.locate(trip, between)).isEqualTo("Chưa xác định được địa chỉ sự cố");
    }
    TripEntity trip() {
        var trip = mock(TripEntity.class);
        var stops = List.of(stop(1, "Bến xe", 10.77, 106.7), stop(2, "Trường học", 10.78, 106.71));
        when(trip.getStops()).thenReturn(stops);
        return trip;
    }
    TripStopEntity stop(int sequence, String name, double lat, double lng) {
        var stop = mock(TripStopEntity.class);
        when(stop.getSequenceNumber()).thenReturn(sequence); when(stop.getStationName()).thenReturn(name);
        when(stop.getLatitude()).thenReturn(BigDecimal.valueOf(lat)); when(stop.getLongitude()).thenReturn(BigDecimal.valueOf(lng));
        when(stop.getCheckinRadiusMeters()).thenReturn(50);
        return stop;
    }
}
