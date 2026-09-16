package com.quangkhai.vehicletracking_backend.trip.service;

import com.quangkhai.vehicletracking_backend.trip.TripFixtures;
import com.quangkhai.vehicletracking_backend.trip.dto.*;
import com.quangkhai.vehicletracking_backend.trip.entity.*;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.trip.event.TripStartedEvent;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleType;
import com.quangkhai.vehicletracking_backend.vehicle.repository.VehicleRepository;
import com.quangkhai.vehicletracking_backend.route.repository.RouteRepository;
import com.quangkhai.vehicletracking_backend.route.entity.RouteEntity;
import com.quangkhai.vehicletracking_backend.station.entity.StationEntity;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {
    @Mock TripRepository trips;
    @Mock VehicleRepository vehicles;
    @Mock RouteRepository routes;
    @Mock ApplicationEventPublisher events;
    @InjectMocks TripService service;
    final Instant departure = Instant.parse("2026-09-13T16:58:00Z");
    VehicleEntity vehicle;
    StationEntity station;
    RouteEntity route;
    @BeforeEach void setup() {
        vehicle = new VehicleEntity("51B12345", "Xe A", null);
        station = TripFixtures.station("A");
        var other = TripFixtures.station("B");
        ReflectionTestUtils.setField(vehicle, "id", 1L);
        ReflectionTestUtils.setField(station, "id", 11L);
        ReflectionTestUtils.setField(other, "id", 12L);
        route = TripFixtures.route(station, other);
        ReflectionTestUtils.setField(route, "id", 2L);
    }
    @Test void create_snapshotsLoopAndScheduleAcrossMidnight() {
        when(vehicles.findLockedById(1)).thenReturn(Optional.of(vehicle));
        when(routes.findById(2L)).thenReturn(Optional.of(route));
        when(trips.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var result = service.create(new TripCreateRequest(1L, 2L, departure));
        assertThat(result.trip().status()).isEqualTo(TripStatus.SCHEDULED);
        assertThat(result.stops()).extracting(TripDetailResponse.Stop::stationId).containsExactly(11L, 12L, 11L);
        assertThat(result.stops().get(1).plannedArrivalAt()).isEqualTo(departure.plusSeconds(300));
        assertThat(result.stops().get(1).plannedDepartureAt()).isEqualTo(departure.plusSeconds(360));
        assertThat(result.trip().plannedEndAt()).isEqualTo(departure.plusSeconds(660));
        station.updateDetails("Changed", null, station.getLatitude(), station.getLongitude(), 500);
        assertThat(result.stops().getFirst().stationName()).isEqualTo("A");
        assertThat(result.stops().getFirst().checkinRadiusMeters()).isEqualTo(50);
    }
    @Test void create_exposesVehicleTypeInTripSummary() {
        vehicle = new VehicleEntity("59X112345", "Xe máy A", null, VehicleType.MOTORCYCLE);
        ReflectionTestUtils.setField(vehicle, "id", 1L);
        when(vehicles.findLockedById(1)).thenReturn(Optional.of(vehicle));
        when(routes.findById(2L)).thenReturn(Optional.of(route));
        when(trips.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        var result = service.create(new TripCreateRequest(1L, 2L, departure));
        assertThat(result.trip().vehicleType()).isEqualTo(VehicleType.MOTORCYCLE);
    }
    @Test void create_rejectsInactiveStation() {
        when(vehicles.findLockedById(1)).thenReturn(Optional.of(vehicle));
        when(routes.findById(2L)).thenReturn(Optional.of(route));
        station.deactivate();
        assertConflict(() -> service.create(new TripCreateRequest(1L, 2L, departure)));
        verify(trips, never()).saveAndFlush(any());
    }
    @Test void create_rejectsInactiveVehicle() {
        vehicle.deactivate();
        when(vehicles.findLockedById(1)).thenReturn(Optional.of(vehicle));
        assertConflict(() -> service.create(new TripCreateRequest(1L, 2L, departure)));
    }
    @Test void create_rejectsMissingRoute() {
        when(vehicles.findLockedById(1)).thenReturn(Optional.of(vehicle));
        assertThatThrownBy(() -> service.create(new TripCreateRequest(1L, 2L, departure)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode().value()).isEqualTo(404));
    }
    @Test void create_rejectsUnsupportedDate() {
        assertThatThrownBy(() -> service.create(new TripCreateRequest(1L, 2L, Instant.parse("2200-01-01T00:00:00Z"))))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }
    @Test void start_blocksSecondRunningTrip() {
        var trip = lockedTrip();
        when(vehicles.findLockedById(1)).thenReturn(Optional.of(vehicle));
        when(trips.existsByVehicleIdAndStatusIn(eq(1L), any())).thenReturn(true);
        assertConflict(() -> service.start(3));
        assertThat(trip.getStatus()).isEqualTo(TripStatus.SCHEDULED);
    }
    @Test void start_isIdempotentAndDoesNotShiftSchedule() {
        var trip = lockedTrip();
        when(vehicles.findLockedById(1)).thenReturn(Optional.of(vehicle));
        var first = service.start(3); var second = service.start(3);
        assertThat(second.trip().startedAt()).isEqualTo(first.trip().startedAt());
        assertThat(trip.getScheduledDepartureAt()).isEqualTo(departure);
        verify(trips, times(1)).flush();
        verify(events).publishEvent(new TripStartedEvent(3L));
    }
    @ParameterizedTest
    @CsvSource({"SCHEDULED,complete", "COMPLETED,start", "COMPLETED,cancel", "CANCELLED,start", "CANCELLED,complete"})
    void rejectsIllegalTransition(TripStatus initial, String action) {
        var trip = lockedTrip();
        ReflectionTestUtils.setField(trip, "status", initial);
        when(vehicles.findLockedById(1)).thenReturn(Optional.of(vehicle));
        assertConflict(() -> { switch(action) { case "start" -> service.start(3); case "complete" -> service.complete(3); default -> service.cancel(3); } });
        assertThat(trip.getStatus()).isEqualTo(initial);
    }
    @Test void complete_andCancelRecordEndTimes() {
        var trip = lockedTrip();
        when(vehicles.findLockedById(1)).thenReturn(Optional.of(vehicle));
        service.start(3); var completed = service.complete(3);
        assertThat(completed.trip().endedAt()).isAfterOrEqualTo(completed.trip().startedAt());
        assertThat(service.complete(3).trip().endedAt()).isEqualTo(completed.trip().endedAt());
        var another = new TripEntity(vehicle, route, departure);
        when(trips.findLockedById(3)).thenReturn(Optional.of(another));
        var cancelled = service.cancel(3);
        assertThat(cancelled.trip().startedAt()).isNull();
        assertThat(cancelled.trip().endedAt()).isNotNull();
        assertThat(service.cancel(3).trip().endedAt()).isEqualTo(cancelled.trip().endedAt());
    }
    private TripEntity lockedTrip() {
        var trip = new TripEntity(vehicle, route, departure);
        ReflectionTestUtils.setField(trip, "id", 3L);
        when(trips.findLockedById(3)).thenReturn(Optional.of(trip));
        return trip;
    }
    private void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode().value()).isEqualTo(409));
    }
}
