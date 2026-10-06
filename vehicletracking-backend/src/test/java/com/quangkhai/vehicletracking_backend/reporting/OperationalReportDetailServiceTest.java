package com.quangkhai.vehicletracking_backend.reporting;

import com.quangkhai.vehicletracking_backend.checkin.entity.TripStopVisitEntity;
import com.quangkhai.vehicletracking_backend.checkin.repository.TripStopVisitRepository;
import com.quangkhai.vehicletracking_backend.config.ReportingProperties;
import com.quangkhai.vehicletracking_backend.reporting.dto.OperationalReportResponse;
import com.quangkhai.vehicletracking_backend.reporting.service.OperationalReportDetailService;
import com.quangkhai.vehicletracking_backend.reporting.service.OperationalReportService;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripNotificationRepository;
import com.quangkhai.vehicletracking_backend.route.entity.RouteEntity;
import com.quangkhai.vehicletracking_backend.schedule.entity.TripScheduleEntity;
import com.quangkhai.vehicletracking_backend.telemetry.repository.TelemetryRepository;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStopEntity;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationalReportDetailServiceTest {
    @Mock OperationalReportService operationalReports;
    @Mock TripRepository trips;
    @Mock TripStopVisitRepository visits;
    @Mock TripNotificationRepository notifications;
    @Mock TelemetryRepository telemetry;
    @Mock Clock clock;
    @Mock VehicleEntity vehicle;
    @Mock RouteEntity route;
    @Mock TripScheduleEntity schedule;
    @Mock TripEntity trip;
    @Mock TripStopEntity stop;
    @Mock TripStopVisitEntity visit;

    @Test
    void groupsLateStopsByVehicleAndDriverAndKeepsEmployeeCountUnknown() {
        ReportingProperties properties = new ReportingProperties();
        Instant departure = Instant.parse("2026-09-21T08:00:00Z");
        OperationalReportResponse summary = new OperationalReportResponse(
                LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 21), departure,
                1, 1, 1000, 600, 0, 1, 0, 0, 80);
        when(operationalReports.operations(summary.from(), summary.to(), null, null)).thenReturn(summary);
        when(clock.instant()).thenReturn(Instant.parse("2026-09-21T12:00:00Z"));
        when(trips.findAllForOperationalReport(Instant.parse("2026-09-21T00:00:00Z"), Instant.parse("2026-09-22T00:00:00Z"), null, null))
                .thenReturn(List.of(trip));
        when(trip.getId()).thenReturn(7L);
        when(trip.getVehicle()).thenReturn(vehicle);
        when(trip.getVehiclePlateSnapshot()).thenReturn("51A-00007");
        when(trip.getDriverNameSnapshot()).thenReturn("Nguyễn Văn A");
        when(trip.getDriver()).thenReturn(null);
        when(trip.getRoute()).thenReturn(route);
        when(route.getName()).thenReturn("Tuyến thử nghiệm");
        when(trip.getSchedule()).thenReturn(schedule);
        when(trip.getStatus()).thenReturn(TripStatus.COMPLETED);
        when(trip.getScheduledDepartureAt()).thenReturn(departure);
        when(trip.getEndedAt()).thenReturn(departure.plusSeconds(600));
        when(trip.getStops()).thenReturn(List.of(stop));
        when(vehicle.getId()).thenReturn(2L);
        when(vehicle.getName()).thenReturn("Xe 7");
        when(stop.getSequenceNumber()).thenReturn(1);
        when(stop.getArrivalOffsetSeconds()).thenReturn(300L);
        when(stop.getStationName()).thenReturn("Bến Thành");
        when(visit.getTrip()).thenReturn(trip);
        when(visit.getStopSequence()).thenReturn(1);
        when(visit.getActualArrivalAt()).thenReturn(departure.plusSeconds(420));
        when(visits.findAllByTripIdInOrderByTripIdAscStopSequenceAsc(List.of(7L))).thenReturn(List.of(visit));
        when(notifications.findAllForOperationalReport(List.of(7L), Instant.parse("2026-09-21T00:00:00Z"),
                Instant.parse("2026-09-22T00:00:00Z"))).thenReturn(List.of());
        when(telemetry.findAllForOperationalReport(List.of(7L), Instant.parse("2026-09-22T00:00:00Z"),
                com.quangkhai.vehicletracking_backend.telemetry.entity.TelemetrySource.GPS)).thenReturn(List.of());

        var result = new OperationalReportDetailService(operationalReports, trips, visits, notifications,
                telemetry, properties, clock).detail(summary.from(), summary.to(), null, null);

        assertThat(result.vehicles()).singleElement().satisfies(row -> {
            assertThat(row.tripCount()).isEqualTo(1);
            assertThat(row.lateStopCount()).isEqualTo(1);
            assertThat(row.employeePassengerCount()).isNull();
        });
        assertThat(result.drivers()).singleElement().extracting(row -> row.driverName()).isEqualTo("Nguyễn Văn A");
        assertThat(result.lateStops()).singleElement().satisfies(row -> assertThat(row.delaySeconds()).isEqualTo(120));
        assertThat(result.lateStops()).singleElement().extracting(row -> row.routeName()).isEqualTo("Tuyến thử nghiệm");
        assertThat(result.employeePassengerDataAvailable()).isFalse();
    }
}
