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

class OperationalReportDriverTripsTest {
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
    void listsExactlyTheStartedTripsForEachDriverWithoutMergingIdenticalNames() {
        DriverEntity a = driver(1), b = driver(2);
        TripEntity completed = trip(10, a), running = trip(11, a), cancelled = trip(12, a);
        completed.start(departure.minusSeconds(600)); completed.complete(departure);
        running.start(departure);
        cancelled.start(departure); cancelled.cancel(departure.plusSeconds(20));
        TripEntity otherDriver = trip(20, b); otherDriver.start(departure);
        TripEntity unnamed = trip(30, null); unnamed.start(departure);
        TripEntity planned = trip(40, a), cancelledBeforeStart = trip(41, a);
        cancelledBeforeStart.cancel(departure);

        var report = report(List.of(planned, completed, otherDriver, running, unnamed, cancelled, cancelledBeforeStart), null, null);

        assertThat(report.drivers()).hasSize(3);
        for (var row : report.drivers()) assertThat(row.trips()).hasSize((int) row.tripCount());
        var aRow = report.drivers().stream().filter(row -> Long.valueOf(1).equals(row.driverId())).findFirst().orElseThrow();
        assertThat(aRow.trips()).extracting(row -> row.tripId()).containsExactly(12L, 11L, 10L);
        assertThat(aRow.trips()).extracting(row -> row.status()).containsExactly(TripStatus.CANCELLED, TripStatus.IN_PROGRESS, TripStatus.COMPLETED);
        assertThat(aRow.trips().get(1).endedAt()).isNull();
        assertThat(aRow.trips().get(2)).satisfies(row -> {
            assertThat(row.routeName()).isEqualTo("Tuyến trường học");
            assertThat(row.vehiclePlateNumber()).isEqualTo("TEST10");
            assertThat(row.scheduledDepartureAt()).isEqualTo(departure);
            assertThat(row.startedAt()).isEqualTo(departure.minusSeconds(600));
            assertThat(row.endedAt()).isEqualTo(departure);
        });
        assertThat(report.drivers().stream().filter(row -> Long.valueOf(2).equals(row.driverId())).findFirst().orElseThrow().trips())
                .singleElement().satisfies(row -> assertThat(row.tripId()).isEqualTo(20));
        assertThat(report.drivers().stream().filter(row -> row.driverId() == null).findFirst().orElseThrow().trips())
                .singleElement().satisfies(row -> assertThat(row.tripId()).isEqualTo(30));
    }

    @Test
    void usesTheSameFilteredQueryAndOnlyCurrentReplayDetails() {
        var replayed = trip(7, driver(3));
        replayed.start(departure.minusSeconds(500)); replayed.complete(departure.minusSeconds(100));
        replayed.replay(departure); replayed.start(departure.plusSeconds(20));
        var report = report(List.of(replayed), 7L, 3L);

        assertThat(report.drivers()).singleElement().satisfies(row -> {
            assertThat(row.tripCount()).isEqualTo(1);
            assertThat(row.trips()).singleElement().satisfies(detail -> {
                assertThat(detail.tripId()).isEqualTo(7);
                assertThat(detail.startedAt()).isEqualTo(departure.plusSeconds(20));
                assertThat(detail.endedAt()).isNull();
                assertThat(detail.status()).isEqualTo(TripStatus.IN_PROGRESS);
            });
        });
        verify(trips).findAllForOperationalReport(from, toExclusive, 7L, 3L);
        verifyNoMoreInteractions(trips);
    }

    private OperationalReportDetailResponse report(List<TripEntity> matchingTrips, Long vehicleId, Long driverId) {
        when(summaries.operations(day, day, vehicleId, driverId)).thenReturn(new OperationalReportResponse(
                day, day, departure, matchingTrips.size(), 0, 0, 0, 0, 0, 0, 0, 80));
        when(trips.findAllForOperationalReport(from, toExclusive, vehicleId, driverId)).thenReturn(matchingTrips);
        return service.detail(day, day, vehicleId, driverId);
    }

    private DriverEntity driver(long id) {
        var driver = new DriverEntity("Nguyễn Văn A", "0000000000", "TEST-" + id);
        ReflectionTestUtils.setField(driver, "id", id);
        return driver;
    }

    private TripEntity trip(long id, DriverEntity driver) {
        var vehicle = new VehicleEntity("TEST" + id, "Xe " + id, null);
        ReflectionTestUtils.setField(vehicle, "id", id);
        var route = BeanUtils.instantiateClass(RouteEntity.class);
        ReflectionTestUtils.setField(route, "name", "Tuyến trường học");
        var trip = new TripEntity(vehicle, route, departure, driver);
        ReflectionTestUtils.setField(trip, "id", id);
        return trip;
    }
}
