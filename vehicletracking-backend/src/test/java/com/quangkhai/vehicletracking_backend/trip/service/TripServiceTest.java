package com.quangkhai.vehicletracking_backend.trip.service;

import com.quangkhai.vehicletracking_backend.driver.entity.DriverEntity;
import com.quangkhai.vehicletracking_backend.driver.repository.DriverRepository;
import com.quangkhai.vehicletracking_backend.trip.TripFixtures;
import com.quangkhai.vehicletracking_backend.trip.dto.*;
import com.quangkhai.vehicletracking_backend.trip.entity.*;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.trip.event.TripStartedEvent;
import com.quangkhai.vehicletracking_backend.checkin.repository.TripStopVisitRepository;
import com.quangkhai.vehicletracking_backend.schedule.entity.ScheduleFrequency;
import com.quangkhai.vehicletracking_backend.schedule.entity.TripScheduleEntity;
import com.quangkhai.vehicletracking_backend.schedule.repository.TripScheduleRepository;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleType;
import com.quangkhai.vehicletracking_backend.vehicle.repository.VehicleRepository;
import com.quangkhai.vehicletracking_backend.route.repository.RouteRepository;
import com.quangkhai.vehicletracking_backend.route.entity.RouteEntity;
import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
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
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {
    @Mock TripRepository trips;
    @Mock VehicleRepository vehicles;
    @Mock DriverRepository drivers;
    @Mock RouteRepository routes;
    @Mock TripScheduleRepository schedules;
    @Mock TripStopVisitRepository visits;
    @Mock Clock operationsClock;
    @Mock ApplicationEventPublisher events;
    @InjectMocks TripService service;
    final Instant departure = Instant.parse("2026-09-13T16:58:00Z");
    VehicleEntity vehicle;
    StationEntity station;
    RouteEntity route;
    @BeforeEach void setup() {
        lenient().when(operationsClock.instant()).thenReturn(departure);
        lenient().when(visits.existsByTripIdAndStopSequence(anyLong(), anyInt())).thenReturn(true);
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
        var result = service.create(new TripCreateRequest(1L, 2L, null));
        assertThat(result.trip().status()).isEqualTo(TripStatus.SCHEDULED);
        assertThat(result.trip().dispatchMode()).isEqualTo(TripDispatchMode.ON_DEMAND);
        assertThat(result.stops()).extracting(stop -> stop.stationId()).containsExactly(11L, 12L, 11L);
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

        var result = service.create(new TripCreateRequest(1L, 2L, null));
        assertThat(result.trip().vehicleType()).isEqualTo(VehicleType.MOTORCYCLE);
    }
    @Test void create_snapshotsSelectedDriverDetails() {
        var driver = new DriverEntity("Nguyễn Văn A", "0901234567", "B2-123");
        ReflectionTestUtils.setField(driver, "id", 9L);
        when(vehicles.findLockedById(1)).thenReturn(Optional.of(vehicle));
        lenient().when(drivers.findLockedById(9L)).thenReturn(Optional.of(driver));
        when(routes.findById(2L)).thenReturn(Optional.of(route));
        when(trips.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        var result = service.create(new TripCreateRequest(1L, 2L, 9L));
        driver.updateDetails("Tên mới", "0987654321", "C-999");

        assertThat(result.trip().driver().fullName()).isEqualTo("Nguyễn Văn A");
        assertThat(result.trip().driver().licenseNumber()).isEqualTo("B2-123");
    }
    @Test void assignDriver_onlyAllowsScheduledTrip() {
        var trip = lockedTrip();
        var driver = new DriverEntity("Nguyễn Văn A", "0901234567", "B2-123");
        ReflectionTestUtils.setField(driver, "id", 9L);
        lenient().when(drivers.findLockedById(9L)).thenReturn(Optional.of(driver));
        when(vehicles.findLockedById(1L)).thenReturn(Optional.of(vehicle));

        var assigned = service.assignDriver(3L, 9L);
        assertThat(assigned.trip().driver().id()).isEqualTo(9L);

        ReflectionTestUtils.setField(trip, "status", TripStatus.IN_PROGRESS);
        assertConflict(() -> service.unassignDriver(3L));
    }
    @Test void assignDriver_rejectsDriverRunningAnotherTrip() {
        var driver = new DriverEntity("Tài xế bận", "0901234567", "BUSY-10");
        ReflectionTestUtils.setField(driver, "id", 10L);
        var trip = new TripEntity(vehicle, route, departure, null);
        ReflectionTestUtils.setField(trip, "id", 3L);
        when(trips.findLockedById(3L)).thenReturn(Optional.of(trip));
        when(vehicles.findLockedById(1L)).thenReturn(Optional.of(vehicle));
        when(drivers.findLockedById(10L)).thenReturn(Optional.of(driver));
        when(trips.existsByDriverIdAndStatusAndIdNot(10L, TripStatus.IN_PROGRESS, 3L)).thenReturn(true);
        assertConflict(() -> service.assignDriver(3L, 10L));
        assertThat(trip.getDriver()).isNull();
        verify(trips, never()).flush();
    }

    @Test void assignDriver_allowsOverlappingScheduledTrip() {
        lockedTrip();
        var driver = new DriverEntity("Nguyễn Văn B", "0907654321", "B2-456");
        ReflectionTestUtils.setField(driver, "id", 10L);
        when(vehicles.findLockedById(1L)).thenReturn(Optional.of(vehicle));
        when(drivers.findLockedById(10L)).thenReturn(Optional.of(driver));

        var assigned = service.assignDriver(3L, 10L);

        assertThat(assigned.trip().driver().id()).isEqualTo(10L);
        verify(trips).flush();
    }
    @Test void assignVehicle_updatesVehicleAndPlateSnapshot() {
        var trip = lockedTrip();
        var replacement = new VehicleEntity("51B99999", "Xe B", null);
        ReflectionTestUtils.setField(replacement, "id", 2L);
        when(vehicles.findLockedById(2L)).thenReturn(Optional.of(replacement));
        when(trips.findAllByVehicleIdAndStatusIn(eq(2L), any())).thenReturn(java.util.List.of());

        var assigned = service.assignVehicle(3L, 2L);

        assertThat(assigned.trip().vehicleId()).isEqualTo(2L);
        assertThat(assigned.trip().vehiclePlateNumber()).isEqualTo("51B99999");
        assertThat(trip.getVehicle()).isSameAs(replacement);
        verify(trips).flush();
    }
    @Test void assignVehicle_rejectsVehicleRunningAnotherTrip() {
        var trip = lockedTrip();
        var replacement = new VehicleEntity("51B99999", "Xe B", null);
        ReflectionTestUtils.setField(replacement, "id", 2L);
        when(vehicles.findLockedById(2L)).thenReturn(Optional.of(replacement));
        when(trips.existsByVehicleIdAndStatusIn(2L, java.util.List.of(TripStatus.IN_PROGRESS))).thenReturn(true);

        assertConflict(() -> service.assignVehicle(3L, 2L));
        assertThat(trip.getVehicle()).isSameAs(vehicle);
    }
    @Test void assignVehicle_rejectsOverlappingFixedSchedule() {
        var trip = lockedTrip();
        var replacement = new VehicleEntity("51B99999", "Xe B", null);
        ReflectionTestUtils.setField(replacement, "id", 2L);
        var schedule = new TripScheduleEntity("Daily", route, replacement, trip.getDriver(), ScheduleFrequency.WEEKLY, null, (short) 1,
                java.time.LocalTime.NOON, "UTC", java.time.LocalDate.of(2026, 9, 1), null);
        var existing = new TripEntity(replacement, route, departure, trip.getDriver(),
                schedule, departure);
        ReflectionTestUtils.setField(existing, "id", 8L);
        when(vehicles.findLockedById(2L)).thenReturn(Optional.of(replacement));
        when(trips.findAllByVehicleIdAndStatusIn(eq(2L), any())).thenReturn(java.util.List.of(existing));

        assertConflict(() -> service.assignVehicle(3L, 2L));
        assertThat(trip.getVehicle()).isSameAs(vehicle);
    }
    @Test void start_rejectsDriverRunningAnotherTrip() {
        var driver = new DriverEntity("Nguyễn Văn A", "0901234567", "B2-123");
        ReflectionTestUtils.setField(driver, "id", 9L);
        var trip = new TripEntity(vehicle, route, departure, driver);
        ReflectionTestUtils.setField(trip, "id", 3L);
        when(trips.findLockedById(3L)).thenReturn(Optional.of(trip));
        when(vehicles.findLockedById(1L)).thenReturn(Optional.of(vehicle));
        when(drivers.findLockedById(9L)).thenReturn(Optional.of(driver));
        when(trips.existsByDriverIdAndStatusAndIdNot(9L, TripStatus.IN_PROGRESS, 3L)).thenReturn(true);

        assertConflict(() -> service.start(3L));
        assertThat(trip.getStatus()).isEqualTo(TripStatus.SCHEDULED);
    }
    @Test void start_rejectsInactiveDriver() {
        var trip = lockedTrip();
        trip.getDriver().deactivate();
        when(vehicles.findLockedById(1L)).thenReturn(Optional.of(vehicle));

        assertConflict(() -> service.start(3L));
        assertThat(trip.getStatus()).isEqualTo(TripStatus.SCHEDULED);
    }
    @Test void start_requiresAssignedDriver() {
        var trip = new TripEntity(vehicle, route, departure);
        ReflectionTestUtils.setField(trip, "id", 3L);
        when(trips.findLockedById(3L)).thenReturn(Optional.of(trip));
        when(vehicles.findLockedById(1L)).thenReturn(Optional.of(vehicle));

        assertConflict(() -> service.start(3L));
        assertThat(trip.getStatus()).isEqualTo(TripStatus.SCHEDULED);
    }
    @Test void start_allowsTripLongAfterScheduledDeparture() {
        when(operationsClock.instant()).thenReturn(departure.plusSeconds(86_400));
        var trip = lockedTrip();
        when(vehicles.findLockedById(1L)).thenReturn(Optional.of(vehicle));

        assertThat(service.start(3L).trip().status()).isEqualTo(TripStatus.IN_PROGRESS);
        assertThat(trip.getScheduledDepartureAt()).isEqualTo(departure);
        assertThat(trip.getStartedAt()).isEqualTo(departure.plusSeconds(86_400));
    }
    @Test void start_allowsTripBeforeScheduledDeparture() {
        when(operationsClock.instant()).thenReturn(departure.minusSeconds(86_400));
        var trip = lockedTrip();
        when(vehicles.findLockedById(1L)).thenReturn(Optional.of(vehicle));

        assertThat(service.start(3L).trip().status()).isEqualTo(TripStatus.IN_PROGRESS);
        assertThat(trip.getScheduledDepartureAt()).isEqualTo(departure);
        assertThat(trip.getStartedAt()).isEqualTo(departure.minusSeconds(86_400));
    }
    @Test void create_rejectsInactiveStation() {
        when(vehicles.findLockedById(1)).thenReturn(Optional.of(vehicle));
        when(routes.findById(2L)).thenReturn(Optional.of(route));
        station.deactivate();
        assertConflict(() -> service.create(new TripCreateRequest(1L, 2L, null)));
        verify(trips, never()).saveAndFlush(any());
    }
    @Test void create_rejectsInactiveVehicle() {
        vehicle.deactivate();
        when(vehicles.findLockedById(1)).thenReturn(Optional.of(vehicle));
        assertConflict(() -> service.create(new TripCreateRequest(1L, 2L, null)));
    }
    @Test void create_rejectsMissingRoute() {
        when(vehicles.findLockedById(1)).thenReturn(Optional.of(vehicle));
        assertThatThrownBy(() -> service.create(new TripCreateRequest(1L, 2L, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode().value()).isEqualTo(404));
    }
    @Test void create_rejectsUnsupportedDate() {
        when(operationsClock.instant()).thenReturn(Instant.parse("2200-01-01T00:00:00Z"));
        assertThatThrownBy(() -> service.create(new TripCreateRequest(1L, 2L, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }
    @Test void delete_rejectsTripGeneratedBySchedule() {
        var schedule = new TripScheduleEntity("Daily", route, vehicle, null, ScheduleFrequency.WEEKLY, null, (short) 1,
                java.time.LocalTime.NOON, "UTC", java.time.LocalDate.of(2026, 9, 1), null);
        var trip = new TripEntity(vehicle, route, departure, null, schedule, departure);
        ReflectionTestUtils.setField(trip, "id", 3L);
        when(trips.findLockedById(3L)).thenReturn(Optional.of(trip));

        assertConflict(() -> service.delete(3L));
        verify(trips, never()).delete(any());
    }
    @Test void scheduledCreation_rejectsVehicleDoubleBooking() {
        var driver = new DriverEntity("Nguyễn Văn A", "0901234567", "B2-123");
        ReflectionTestUtils.setField(driver, "id", 9L);
        var schedule = new TripScheduleEntity("Daily", route, vehicle, driver, ScheduleFrequency.WEEKLY, null, (short) 1,
                java.time.LocalTime.NOON, "UTC", java.time.LocalDate.of(2026, 9, 1), null);
        ReflectionTestUtils.setField(schedule, "id", 7L);
        when(schedules.getReferenceById(7L)).thenReturn(schedule);
        when(vehicles.findLockedById(1L)).thenReturn(Optional.of(vehicle));
        when(drivers.findLockedById(9L)).thenReturn(Optional.of(driver));
        when(routes.findLockedById(2L)).thenReturn(Optional.of(route));
        var existing = new TripEntity(vehicle, route, departure, driver, schedule, departure);
        ReflectionTestUtils.setField(existing, "id", 8L);
        when(trips.findAllByVehicleIdAndStatusIn(eq(1L), any())).thenReturn(java.util.List.of(existing));

        assertConflict(() -> service.createFromSchedule(schedule, departure));
        verify(trips, never()).saveAndFlush(any());
    }
    @Test void scheduledCreation_allowsDriverOverlapOnAnotherVehicle() {
        var driver = new DriverEntity("Nguyễn Văn A", "0901234567", "B2-123");
        ReflectionTestUtils.setField(driver, "id", 9L);
        var schedule = new TripScheduleEntity("Daily", route, vehicle, driver, ScheduleFrequency.WEEKLY, null, (short) 1,
                java.time.LocalTime.NOON, "UTC", java.time.LocalDate.of(2026, 9, 1), null);
        ReflectionTestUtils.setField(schedule, "id", 7L);
        when(schedules.getReferenceById(7L)).thenReturn(schedule);
        when(vehicles.findLockedById(1L)).thenReturn(Optional.of(vehicle));
        when(drivers.findLockedById(9L)).thenReturn(Optional.of(driver));
        when(routes.findLockedById(2L)).thenReturn(Optional.of(route));
        when(trips.findAllByVehicleIdAndStatusIn(eq(1L), any())).thenReturn(java.util.List.of());
        when(trips.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        var created = service.createFromSchedule(schedule, departure).trip();
        assertThat(created.driver().id()).isEqualTo(9L);
        assertThat(created.dispatchMode()).isEqualTo(TripDispatchMode.FIXED_SCHEDULE);
        assertThat(created.scheduleId()).isEqualTo(7L);
        assertThat(created.scheduleName()).isEqualTo("Daily");
    }
    @Test void scheduledCreation_allowsFutureTripWhenRunningTripIntervalDoesNotOverlap() {
        var driver = new DriverEntity("Nguyễn Văn A", "0901234567", "B2-123");
        ReflectionTestUtils.setField(driver, "id", 9L);
        var schedule = new TripScheduleEntity("Daily", route, vehicle, driver, ScheduleFrequency.WEEKLY, null, (short) 1,
                java.time.LocalTime.NOON, "UTC", java.time.LocalDate.of(2026, 9, 1), null);
        ReflectionTestUtils.setField(schedule, "id", 7L);
        var running = new TripEntity(vehicle, route, departure.minusSeconds(660), driver);
        ReflectionTestUtils.setField(running, "id", 8L);
        ReflectionTestUtils.setField(running, "status", TripStatus.IN_PROGRESS);
        when(schedules.getReferenceById(7L)).thenReturn(schedule);
        when(vehicles.findLockedById(1L)).thenReturn(Optional.of(vehicle));
        when(drivers.findLockedById(9L)).thenReturn(Optional.of(driver));
        when(routes.findLockedById(2L)).thenReturn(Optional.of(route));
        when(trips.findAllByVehicleIdAndStatusIn(eq(1L), any())).thenReturn(java.util.List.of(running));
        when(trips.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        assertThat(service.createFromSchedule(schedule, departure).trip().status()).isEqualTo(TripStatus.SCHEDULED);
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
        lockedTrip();
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
    @Test void complete_requiresFinalStopVisit() {
        var trip = lockedTrip();
        when(vehicles.findLockedById(1L)).thenReturn(Optional.of(vehicle));
        service.start(3);
        when(visits.existsByTripIdAndStopSequence(3L, 3)).thenReturn(false);

        assertConflict(() -> service.complete(3));
        assertThat(trip.getStatus()).isEqualTo(TripStatus.IN_PROGRESS);
    }
    @Test void cancelStoresReason() {
        lockedTrip();
        when(vehicles.findLockedById(1L)).thenReturn(Optional.of(vehicle));

        var cancelled = service.cancel(3, "Xe gặp sự cố");

        assertThat(cancelled.trip().cancellationReason()).isEqualTo("Xe gặp sự cố");
    }
    @Test void cancelRejectsBlankReason() {
        assertThatThrownBy(() -> service.cancel(3L, "  "))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
        verifyNoInteractions(trips);
    }
    private TripEntity lockedTrip() {
        var driver = new DriverEntity("Nguyễn Văn A", "0901234567", "B2-123");
        ReflectionTestUtils.setField(driver, "id", 9L);
        lenient().when(drivers.findLockedById(9L)).thenReturn(Optional.of(driver));
        var trip = new TripEntity(vehicle, route, departure, driver);
        RouteDetailResponse.from(route).stops().forEach(stop -> trip.addStop(new TripStopEntity(stop, 50, departure)));
        ReflectionTestUtils.setField(trip, "id", 3L);
        when(trips.findLockedById(3)).thenReturn(Optional.of(trip));
        return trip;
    }
    private void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode().value()).isEqualTo(409));
    }
}
