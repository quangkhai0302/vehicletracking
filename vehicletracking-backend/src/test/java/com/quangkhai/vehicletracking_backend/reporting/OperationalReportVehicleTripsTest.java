package com.quangkhai.vehicletracking_backend.reporting;

import com.quangkhai.vehicletracking_backend.checkin.repository.TripStopVisitRepository;
import com.quangkhai.vehicletracking_backend.config.ReportingProperties;
import com.quangkhai.vehicletracking_backend.driver.entity.DriverEntity;
import com.quangkhai.vehicletracking_backend.reporting.dto.OperationalReportDetailResponse;
import com.quangkhai.vehicletracking_backend.reporting.dto.OperationalReportResponse;
import com.quangkhai.vehicletracking_backend.reporting.service.OperationalReportDetailService;
import com.quangkhai.vehicletracking_backend.reporting.service.OperationalReportService;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripNotificationRepository;
import com.quangkhai.vehicletracking_backend.route.entity.RouteEntity;
import com.quangkhai.vehicletracking_backend.telemetry.repository.TelemetryRepository;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OperationalReportVehicleTripsTest {
    private final Instant departure = Instant.parse("2026-10-07T01:00:00Z");
    private final LocalDate day = LocalDate.of(2026, 10, 7);
    private final Instant from = Instant.parse("2026-10-06T17:00:00Z");
    private final Instant toExclusive = Instant.parse("2026-10-07T17:00:00Z");
    private final TripRepository trips = mock(TripRepository.class);
    private final OperationalReportService summaries = mock(OperationalReportService.class);
    private final OperationalReportDetailService service = new OperationalReportDetailService(summaries, trips,
            mock(TripStopVisitRepository.class), mock(TripNotificationRepository.class),
            mock(TelemetryRepository.class), new ReportingProperties(), Clock.fixed(departure, ZoneOffset.UTC));

    @Test
    void listsStartedTripsByVehicleIdWithDifferentDriversAndLifecycleStates() {
        var a = vehicle(1, "TEST-PLATE");
        var b = vehicle(2, "TEST-PLATE");
        var completed = trip(10, a, driver(1));
        completed.start(departure.minusSeconds(600)); completed.complete(departure);
        var running = trip(11, a, driver(2)); running.start(departure);
        var cancelled = trip(12, a, null); cancelled.start(departure); cancelled.cancel(departure.plusSeconds(20));
        ReflectionTestUtils.setField(cancelled, "driverNameSnapshot", "Tài xế lịch sử");
        var otherVehicle = trip(20, b, driver(1)); otherVehicle.start(departure);
        ReflectionTestUtils.setField(otherVehicle, "route", null);
        var planned = trip(30, a, driver(1));
        var cancelledBeforeStart = trip(31, a, driver(1)); cancelledBeforeStart.cancel(departure);

        var report = report(List.of(planned, completed, otherVehicle, running, cancelled, cancelledBeforeStart), null, null);

        assertThat(report.vehicles()).hasSize(2);
        for (var row : report.vehicles()) assertThat(row.trips()).hasSize((int) row.tripCount());
        var row = report.vehicles().stream().filter(v -> v.vehicleId().equals(1L)).findFirst().orElseThrow();
        assertThat(row.trips()).extracting(t -> t.tripId()).containsExactly(12L, 11L, 10L);
        assertThat(row.trips()).extracting(t -> t.status()).containsExactly(TripStatus.CANCELLED, TripStatus.IN_PROGRESS, TripStatus.COMPLETED);
        assertThat(row.trips()).extracting(t -> t.driverName()).containsExactly("Tài xế lịch sử", "Tài xế 2", "Tài xế 1");
        assertThat(row.trips().get(1).endedAt()).isNull();
        assertThat(row.trips().get(2)).satisfies(detail -> {
            assertThat(detail.routeName()).isEqualTo("Tuyến trường học");
            assertThat(detail.scheduledDepartureAt()).isEqualTo(departure);
            assertThat(detail.startedAt()).isEqualTo(departure.minusSeconds(600));
            assertThat(detail.endedAt()).isEqualTo(departure);
        });
        assertThat(report.vehicles().stream().filter(v -> v.vehicleId().equals(2L)).findFirst().orElseThrow().trips())
                .singleElement().satisfies(detail -> {
                    assertThat(detail.tripId()).isEqualTo(20);
                    assertThat(detail.routeName()).isNull();
                });
    }

    @Test
    void changingAPlateDoesNotSplitOneVehiclesTripList() {
        var vehicle = vehicle(1, "OLD-PLATE");
        var before = trip(10, vehicle, driver(1)); before.start(departure);
        vehicle.updateDetails("NEW-PLATE", "Xe mới", null);
        var after = trip(11, vehicle, null); after.start(departure.plusSeconds(100));

        var report = report(List.of(before, after), null, null);

        assertThat(report.vehicles()).singleElement().satisfies(row -> {
            assertThat(row.plateNumber()).isEqualTo("NEW-PLATE");
            assertThat(row.vehicleName()).isEqualTo("Xe mới");
            assertThat(row.tripCount()).isEqualTo(2);
            assertThat(row.trips()).extracting(t -> t.tripId()).containsExactly(11L, 10L);
            assertThat(row.trips().getFirst().driverName()).isNull();
        });
        assertThat(before.getVehiclePlateSnapshot()).isEqualTo("OLD-PLATE");
        assertThat(report.employeeOccupancyByVehicle()).hasSize(1);
    }

    @Test
    void keepsReportFiltersAndReturnsCurrentReplayDetailsWithoutAnotherQuery() {
        var replayed = trip(7, vehicle(8, "TEST-8"), driver(3));
        replayed.start(departure.minusSeconds(500)); replayed.complete(departure.minusSeconds(100));
        replayed.replay(departure); replayed.start(departure.plusSeconds(20));
        var report = report(List.of(replayed), 8L, 3L);

        assertThat(report.vehicles()).singleElement().satisfies(row -> {
            assertThat(row.vehicleId()).isEqualTo(8);
            assertThat(row.tripCount()).isEqualTo(1);
            assertThat(row.trips()).singleElement().satisfies(detail -> {
                assertThat(detail.tripId()).isEqualTo(7);
                assertThat(detail.startedAt()).isEqualTo(departure.plusSeconds(20));
                assertThat(detail.endedAt()).isNull();
                assertThat(detail.status()).isEqualTo(TripStatus.IN_PROGRESS);
            });
        });
        verify(trips).findAllForOperationalReport(from, toExclusive, 8L, 3L);
        verifyNoMoreInteractions(trips);
    }

    private OperationalReportDetailResponse report(List<TripEntity> matchingTrips, Long vehicleId, Long driverId) {
        when(summaries.operations(day, day, vehicleId, driverId)).thenReturn(new OperationalReportResponse(
                day, day, departure, matchingTrips.size(), 0, 0, 0, 0, 0, 0, 0, 80));
        when(trips.findAllForOperationalReport(from, toExclusive, vehicleId, driverId)).thenReturn(matchingTrips);
        return service.detail(day, day, vehicleId, driverId);
    }

    private DriverEntity driver(long id) {
        var driver = new DriverEntity("Tài xế " + id, "0000000000", "TEST-" + id);
        ReflectionTestUtils.setField(driver, "id", id);
        return driver;
    }

    private VehicleEntity vehicle(long id, String plate) {
        var vehicle = new VehicleEntity(plate, "Xe trường học", null);
        ReflectionTestUtils.setField(vehicle, "id", id);
        return vehicle;
    }

    private TripEntity trip(long id, VehicleEntity vehicle, DriverEntity driver) {
        var route = BeanUtils.instantiateClass(RouteEntity.class);
        ReflectionTestUtils.setField(route, "name", "Tuyến trường học");
        var trip = new TripEntity(vehicle, route, departure, driver);
        ReflectionTestUtils.setField(trip, "id", id);
        return trip;
    }
}
