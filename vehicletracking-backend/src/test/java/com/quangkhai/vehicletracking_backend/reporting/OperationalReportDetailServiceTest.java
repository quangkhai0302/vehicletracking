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
        when(trips.findAllForOperationalReport(Instant.parse("2026-09-20T17:00:00Z"), Instant.parse("2026-09-21T17:00:00Z"), null, null))
                .thenReturn(List.of(trip));
        when(trip.getId()).thenReturn(7L);
        when(trip.getStartedAt()).thenReturn(departure);
        when(trip.getVehicle()).thenReturn(vehicle);
        when(trip.getVehiclePlateSnapshot()).thenReturn("51A-00007");
        when(trip.getDriverNameSnapshot()).thenReturn("Nguyễn Văn A");
        when(trip.getDriver()).thenReturn(null);
        when(trip.getRoute()).thenReturn(route);
        when(route.getName()).thenReturn("Tuyến thử nghiệm");
        when(trip.getStatus()).thenReturn(TripStatus.COMPLETED);
        when(trip.getScheduledDepartureAt()).thenReturn(departure);
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
        when(notifications.findAllForOperationalReport(List.of(7L), Instant.parse("2026-09-20T17:00:00Z"),
                Instant.parse("2026-09-21T17:00:00Z"))).thenReturn(List.of());
        when(telemetry.findAllForOperationalReport(List.of(7L), Instant.parse("2026-09-21T17:00:00Z"))).thenReturn(List.of());

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

    @Test
    void calculatesPerTripSeatUtilizationFromConfirmedPickupCounts() {
        ReportingProperties properties = new ReportingProperties();
        Instant departure = Instant.parse("2026-09-21T08:00:00Z");
        OperationalReportResponse summary = new OperationalReportResponse(
                LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 21), departure,
                1, 1, 1000, 900, 0, 0, 0, 0, 80);
        when(operationalReports.operations(summary.from(), summary.to(), null, null)).thenReturn(summary);
        when(clock.instant()).thenReturn(departure.plusSeconds(1200));
        when(trips.findAllForOperationalReport(Instant.parse("2026-09-20T17:00:00Z"),
                Instant.parse("2026-09-21T17:00:00Z"), null, null)).thenReturn(List.of(trip));
        when(trip.getId()).thenReturn(7L);
        when(trip.getStartedAt()).thenReturn(departure);
        when(trip.getVehicle()).thenReturn(vehicle);
        when(vehicle.getId()).thenReturn(2L);
        when(vehicle.getName()).thenReturn("Xe 7");
        when(vehicle.getSeatCapacity()).thenReturn(10);
        when(trip.getSeatCapacitySnapshot()).thenReturn(10);
        when(trip.getVehiclePlateSnapshot()).thenReturn("51A-00007");
        when(trip.getDriverNameSnapshot()).thenReturn("Nguyễn Văn A");
        when(trip.getDriver()).thenReturn(null);
        when(trip.getStatus()).thenReturn(TripStatus.COMPLETED);
        when(stop.getSequenceNumber()).thenReturn(1);
        when(stop.getArrivalOffsetSeconds()).thenReturn(0L);
        TripStopEntity secondStop = stopAtSecondPickup();
        TripStopEntity endStop = finalStop();
        when(trip.getStops()).thenReturn(List.of(stop, secondStop, endStop));
        when(secondStop.getSequenceNumber()).thenReturn(2);
        when(secondStop.getArrivalOffsetSeconds()).thenReturn(360L);
        when(endStop.getSequenceNumber()).thenReturn(3);
        when(endStop.getArrivalOffsetSeconds()).thenReturn(1020L);

        TripStopVisitEntity firstVisit = org.mockito.Mockito.mock(TripStopVisitEntity.class);
        TripStopVisitEntity secondVisit = org.mockito.Mockito.mock(TripStopVisitEntity.class);
        TripStopVisitEntity finalVisit = org.mockito.Mockito.mock(TripStopVisitEntity.class);
        when(firstVisit.getTrip()).thenReturn(trip);
        when(firstVisit.getStopSequence()).thenReturn(1);
        when(firstVisit.getActualArrivalAt()).thenReturn(departure);
        when(firstVisit.getSimulatedArrivalAt()).thenReturn(departure);
        when(firstVisit.getEmployeeBoardingCount()).thenReturn(2);
        when(secondVisit.getTrip()).thenReturn(trip);
        when(secondVisit.getStopSequence()).thenReturn(2);
        when(secondVisit.getActualArrivalAt()).thenReturn(departure.plusSeconds(360));
        when(secondVisit.getSimulatedArrivalAt()).thenReturn(departure.plusSeconds(300));
        when(secondVisit.getEmployeeBoardingCount()).thenReturn(3);
        when(finalVisit.getTrip()).thenReturn(trip);
        when(finalVisit.getStopSequence()).thenReturn(3);
        when(finalVisit.getActualArrivalAt()).thenReturn(departure.plusSeconds(1020));
        when(finalVisit.getSimulatedArrivalAt()).thenReturn(departure.plusSeconds(900));
        when(visits.findAllByTripIdInOrderByTripIdAscStopSequenceAsc(List.of(7L)))
                .thenReturn(List.of(firstVisit, secondVisit, finalVisit));
        when(notifications.findAllForOperationalReport(List.of(7L), Instant.parse("2026-09-20T17:00:00Z"),
                Instant.parse("2026-09-21T17:00:00Z"))).thenReturn(List.of());
        when(telemetry.findAllForOperationalReport(List.of(7L), Instant.parse("2026-09-21T17:00:00Z"))).thenReturn(List.of());

        var result = new OperationalReportDetailService(operationalReports, trips, visits, notifications,
                telemetry, properties, clock).detail(summary.from(), summary.to(), null, null);

        assertThat(result.employeeOccupancy().totalBoardings()).isEqualTo(5);
        assertThat(result.employeeOccupancy().averageBoardingsPerTrip()).isEqualTo(5d);
        assertThat(result.employeeOccupancy().averageOnboard()).isNull();
        assertThat(result.employeeOccupancy().seatUtilizationPercent()).isEqualTo(50d);
        assertThat(result.employeeOccupancy().tripsWithCompleteBoardingData()).isEqualTo(1);
        assertThat(result.employeeOccupancyByVehicle()).singleElement().satisfies(row -> {
            assertThat(row.seatCapacity()).isEqualTo(10);
            assertThat(row.totalBoardings()).isEqualTo(5);
            assertThat(row.seatUtilizationPercent()).isEqualTo(50d);
        });
    }

    private TripStopEntity stopAtSecondPickup() { return org.mockito.Mockito.mock(TripStopEntity.class); }
    private TripStopEntity finalStop() { return org.mockito.Mockito.mock(TripStopEntity.class); }
}
