package com.quangkhai.vehicletracking_backend.trip.service;

import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
import com.quangkhai.vehicletracking_backend.traffic.TrafficSource;
import com.quangkhai.vehicletracking_backend.traffic.TrafficStatus;
import com.quangkhai.vehicletracking_backend.traffic.eta.TrafficEtaService;
import com.quangkhai.vehicletracking_backend.traffic.eta.TripEtaResponse;
import com.quangkhai.vehicletracking_backend.trip.TripFixtures;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStopEntity;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripStartScheduleServiceTest {
    @Mock TripRepository trips;
    @Mock TrafficEtaService trafficEta;
    @InjectMocks TripStartScheduleService service;

    @Test
    void refreshesOnlyTheActiveTripSnapshotFromCurrentTrafficEta() {
        Instant originalDeparture = Instant.parse("2026-09-16T09:00:00Z");
        Instant calculatedAt = Instant.parse("2026-09-16T09:06:00Z");
        VehicleEntity vehicle = new VehicleEntity("51B12345", "Xe A", null);
        ReflectionTestUtils.setField(vehicle, "id", 1L);
        var route = TripFixtures.route(TripFixtures.station("A"), TripFixtures.station("B"));
        TripEntity trip = new TripEntity(vehicle, route, originalDeparture);
        ReflectionTestUtils.setField(trip, "id", 7L);
        RouteDetailResponse detail = RouteDetailResponse.from(route);
        detail.stops().forEach(stop -> trip.addStop(new TripStopEntity(stop, 50, originalDeparture)));
        trip.start(calculatedAt);
        when(trips.findById(7L)).thenReturn(Optional.of(trip));
        when(trafficEta.calculateAtStart(7L)).thenReturn(new TripEtaResponse(7L, 2L, calculatedAt,
                TrafficSource.HERE_LIVE, TrafficStatus.AVAILABLE, calculatedAt, calculatedAt, 1,
                420, 420,
                List.of(new TripEtaResponse.EtaStop(2, "B", "NEXT", calculatedAt.plusSeconds(100), 100L, null, TrafficSource.HERE_LIVE),
                        new TripEtaResponse.EtaStop(3, "A", "PLANNED", calculatedAt.plusSeconds(400), 400L, null, TrafficSource.HERE_LIVE)),
                List.of(), null));

        service.refresh(7L);

        assertThat(trip.getStops()).extracting(TripStopEntity::getPlannedArrivalAt)
                .containsExactly(calculatedAt, calculatedAt.plusSeconds(100), calculatedAt.plusSeconds(400));
        assertThat(trip.getStops().get(1).getPlannedDepartureAt()).isEqualTo(calculatedAt.plusSeconds(160));
        verify(trips).flush();
    }

    @Test
    void keepsSavedScheduleWhenHereCannotProvideUsableEta() {
        Instant originalDeparture = Instant.parse("2026-09-16T09:00:00Z");
        VehicleEntity vehicle = new VehicleEntity("51B12345", "Xe A", null);
        ReflectionTestUtils.setField(vehicle, "id", 1L);
        var route = TripFixtures.route(TripFixtures.station("A"), TripFixtures.station("B"));
        TripEntity trip = new TripEntity(vehicle, route, originalDeparture);
        ReflectionTestUtils.setField(trip, "id", 7L);
        RouteDetailResponse.from(route).stops().forEach(stop -> trip.addStop(new TripStopEntity(stop, 50, originalDeparture)));
        trip.start(originalDeparture);
        Instant savedArrival = trip.getStops().get(1).getPlannedArrivalAt();
        when(trips.findById(7L)).thenReturn(Optional.of(trip));
        when(trafficEta.calculateAtStart(7L)).thenReturn(new TripEtaResponse(7L, 2L, originalDeparture,
                TrafficSource.ROUTE_SNAPSHOT, TrafficStatus.UNAVAILABLE, null, originalDeparture, 1,
                660, List.of(), List.of(), "Traffic provider unavailable; using route snapshot"));

        service.refresh(7L);

        assertThat(trip.getStops().get(1).getPlannedArrivalAt()).isEqualTo(savedArrival);
        verify(trips, never()).flush();
    }
}
