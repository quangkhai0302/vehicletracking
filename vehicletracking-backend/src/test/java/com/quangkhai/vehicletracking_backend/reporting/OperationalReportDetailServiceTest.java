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
import static org.mockito.Mockito.mock;

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
        TripStopEntity missingPickup = mock(TripStopEntity.class);
        TripStopEntity terminal = mock(TripStopEntity.class);
        TripStopVisitEntity terminalVisit = mock(TripStopVisitEntity.class);
        when(trip.getStops()).thenReturn(List.of(stop, missingPickup, terminal));
        when(vehicle.getId()).thenReturn(2L);
        when(vehicle.getName()).thenReturn("Xe 7");
        when(stop.getSequenceNumber()).thenReturn(1);
        when(stop.getArrivalOffsetSeconds()).thenReturn(300L);
        when(stop.getStationName()).thenReturn("Bến Thành");
        when(missingPickup.getSequenceNumber()).thenReturn(2);
        when(missingPickup.getStationName()).thenReturn("Trạm chưa xác nhận");
        when(terminal.getSequenceNumber()).thenReturn(3);
        when(visit.getTrip()).thenReturn(trip);
        when(visit.getStopSequence()).thenReturn(1);
        when(visit.getActualArrivalAt()).thenReturn(departure.plusSeconds(420));
        when(visit.getEmployeeBoardingCount()).thenReturn(0);
        when(terminalVisit.getTrip()).thenReturn(trip);
        when(terminalVisit.getStopSequence()).thenReturn(3);
        when(visits.findAllByTripIdInOrderByTripIdAscStopSequenceAsc(List.of(7L)))
                .thenReturn(List.of(visit, terminalVisit));
        var incident = new com.quangkhai.vehicletracking_backend.simulation.entity.SimulationIncidentEntity(trip,
                new com.quangkhai.vehicletracking_backend.driver.entity.DriverEntity("Tài xế báo sự cố", "0901234567", "LIC-REPORT"), 1,
                com.quangkhai.vehicletracking_backend.simulation.entity.SimulationIncidentType.VEHICLE_BREAKDOWN,
                com.quangkhai.vehicletracking_backend.reroute.entity.NotificationSeverity.MAJOR,
                "Kiểm tra động cơ", 10.77, 106.7, 7, java.util.UUID.randomUUID(), departure);
        incident.resolve(departure.plusSeconds(60), "Đã kiểm tra xong");
        var reportNotice = mock(com.quangkhai.vehicletracking_backend.reroute.entity.TripNotificationEntity.class);
        var resolvedNotice = mock(com.quangkhai.vehicletracking_backend.reroute.entity.TripNotificationEntity.class);
        when(reportNotice.getTrip()).thenReturn(trip);
        when(resolvedNotice.getTrip()).thenReturn(trip);
        when(reportNotice.getType()).thenReturn(com.quangkhai.vehicletracking_backend.reroute.entity.NotificationType.SIMULATION_INCIDENT);
        when(resolvedNotice.getType()).thenReturn(com.quangkhai.vehicletracking_backend.reroute.entity.NotificationType.SIMULATION_INCIDENT_RESOLVED);
        when(reportNotice.getSimulationIncident()).thenReturn(incident);
        when(resolvedNotice.getSimulationIncident()).thenReturn(incident);
        when(notifications.findAllForOperationalReport(List.of(7L), Instant.parse("2026-09-20T17:00:00Z"),
                Instant.parse("2026-09-21T17:00:00Z"))).thenReturn(List.of(reportNotice, resolvedNotice));
        when(telemetry.findAllForOperationalReport(List.of(7L), Instant.parse("2026-09-21T17:00:00Z"))).thenReturn(List.of());

        var result = new OperationalReportDetailService(operationalReports, trips, visits, notifications,
                telemetry, properties, clock).detail(summary.from(), summary.to(), null, null);

        assertThat(result.vehicles()).singleElement().satisfies(row -> {
            assertThat(row.tripCount()).isEqualTo(1);
            assertThat(row.lateStopCount()).isEqualTo(1);
            assertThat(row.employeePassengerCount()).isNull();
        });
        assertThat(result.incidentDetails()).hasSize(1);
        assertThat(result.incidents()).singleElement().satisfies(row -> assertThat(row.count()).isEqualTo(1));
        assertThat(result.drivers()).singleElement().extracting(row -> row.driverName()).isEqualTo("Nguyễn Văn A");
        assertThat(result.lateStops()).singleElement().satisfies(row -> assertThat(row.delaySeconds()).isEqualTo(120));
        assertThat(result.lateStops()).singleElement().extracting(row -> row.routeName()).isEqualTo("Tuyến thử nghiệm");
        assertThat(result.employeePassengerDataAvailable()).isFalse();
        assertThat(result.employeeOccupancyByStation()).isEmpty();
        assertThat(result.employeeOccupancyTrips()).singleElement().satisfies(row -> {
            assertThat(row.complete()).isFalse();
            assertThat(row.totalBoardings()).isNull();
            assertThat(row.pickupStops()).extracting(stopRow -> stopRow.boardingCount())
                    .containsExactly(0, null);
            assertThat(row.pickupStops()).extracting(stopRow -> stopRow.onboardAfterStop())
                    .containsExactly(0L, null);
        });
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
        when(trip.getScheduledDepartureAt()).thenReturn(departure);
        when(stop.getSequenceNumber()).thenReturn(1);
        when(stop.getStationId()).thenReturn(11L);
        when(stop.getStationName()).thenReturn("Trạm A");
        when(stop.getArrivalOffsetSeconds()).thenReturn(0L);
        TripStopEntity secondStop = stopAtSecondPickup();
        TripStopEntity endStop = finalStop();
        when(trip.getStops()).thenReturn(List.of(stop, secondStop, endStop));
        when(secondStop.getSequenceNumber()).thenReturn(2);
        when(secondStop.getStationId()).thenReturn(12L);
        when(secondStop.getStationName()).thenReturn("Trạm B");
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
        assertThat(result.employeeOccupancyByDay()).singleElement().satisfies(row -> {
            assertThat(row.date()).isEqualTo(LocalDate.of(2026, 9, 21));
            assertThat(row.confirmedTripCount()).isEqualTo(1);
            assertThat(row.totalBoardings()).isEqualTo(5);
            assertThat(row.seatUtilizationPercent()).isEqualTo(50d);
        });
        assertThat(result.employeeOccupancyByStation()).extracting(row -> row.totalBoardings())
                .containsExactly(3L, 2L);
        assertThat(result.employeeOccupancyTrips()).singleElement().satisfies(row -> {
            assertThat(row.complete()).isTrue();
            assertThat(row.totalBoardings()).isEqualTo(5L);
            assertThat(row.pickupStops()).extracting(stopRow -> stopRow.onboardAfterStop())
                    .containsExactly(2L, 5L);
        });
    }

    @Test
    void groupsConfirmedPickupCountsByVietnameseServiceDayAndStationIncludingZero() {
        Instant beforeMidnight = Instant.parse("2026-09-21T16:30:00Z");
        Instant afterMidnight = Instant.parse("2026-09-21T17:30:00Z");
        Instant now = Instant.parse("2026-09-22T12:00:00Z");
        var summary = new OperationalReportResponse(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 22),
                now, 2, 2, 0, 0, 0, 0, 0, 0, 80);
        TripEntity first = mock(TripEntity.class);
        TripEntity second = mock(TripEntity.class);
        when(operationalReports.operations(summary.from(), summary.to(), null, null)).thenReturn(summary);
        when(clock.instant()).thenReturn(now);
        when(trips.findAllForOperationalReport(Instant.parse("2026-09-20T17:00:00Z"),
                Instant.parse("2026-09-22T17:00:00Z"), null, null)).thenReturn(List.of(first, second));
        when(vehicle.getId()).thenReturn(5L);
        when(vehicle.getSeatCapacity()).thenReturn(10);

        List<TripStopVisitEntity> visitRows = new java.util.ArrayList<>();
        TripEntity[] tripRows = {first, second};
        Instant[] departures = {beforeMidnight, afterMidnight};
        for (int index = 0; index < tripRows.length; index++) {
            TripEntity current = tripRows[index];
            TripStopEntity pickup = mock(TripStopEntity.class);
            TripStopEntity terminal = mock(TripStopEntity.class);
            TripStopVisitEntity boarding = mock(TripStopVisitEntity.class);
            TripStopVisitEntity arrival = mock(TripStopVisitEntity.class);
            when(current.getId()).thenReturn((long) index + 1);
            when(current.getVehicle()).thenReturn(vehicle);
            when(current.getStatus()).thenReturn(TripStatus.COMPLETED);
            when(current.getScheduledDepartureAt()).thenReturn(departures[index]);
            when(current.getStartedAt()).thenReturn(departures[index]);
            when(current.getSeatCapacitySnapshot()).thenReturn(10);
            when(current.getStops()).thenReturn(List.of(pickup, terminal));
            when(pickup.getSequenceNumber()).thenReturn(1);
            when(pickup.getStationId()).thenReturn(11L);
            when(pickup.getStationName()).thenReturn("Trạm A");
            when(terminal.getSequenceNumber()).thenReturn(2);
            when(boarding.getTrip()).thenReturn(current);
            when(boarding.getStopSequence()).thenReturn(1);
            when(boarding.getEmployeeBoardingCount()).thenReturn(index == 0 ? 0 : 3);
            when(arrival.getTrip()).thenReturn(current);
            when(arrival.getStopSequence()).thenReturn(2);
            visitRows.add(boarding);
            visitRows.add(arrival);
        }
        when(visits.findAllByTripIdInOrderByTripIdAscStopSequenceAsc(List.of(1L, 2L)))
                .thenReturn(visitRows);
        when(notifications.findAllForOperationalReport(List.of(1L, 2L),
                Instant.parse("2026-09-20T17:00:00Z"), Instant.parse("2026-09-22T17:00:00Z")))
                .thenReturn(List.of());
        when(telemetry.findAllForOperationalReport(List.of(1L, 2L),
                Instant.parse("2026-09-22T17:00:00Z"))).thenReturn(List.of());

        var result = new OperationalReportDetailService(operationalReports, trips, visits, notifications,
                telemetry, new ReportingProperties(), clock).detail(summary.from(), summary.to(), null, null);

        assertThat(result.employeeOccupancyByDay()).extracting(row -> row.date())
                .containsExactly(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 22));
        assertThat(result.employeeOccupancyByDay()).extracting(row -> row.totalBoardings())
                .containsExactly(0L, 3L);
        assertThat(result.employeeOccupancyByStation()).singleElement().satisfies(row -> {
            assertThat(row.stationId()).isEqualTo(11L);
            assertThat(row.visitCount()).isEqualTo(2);
            assertThat(row.totalBoardings()).isEqualTo(3);
            assertThat(row.averageBoardingsPerVisit()).isEqualTo(1.5d);
        });
        assertThat(result.employeeOccupancyTrips()).extracting(row -> row.totalBoardings())
                .containsExactly(3L, 0L);
    }

    private TripStopEntity stopAtSecondPickup() { return org.mockito.Mockito.mock(TripStopEntity.class); }
    private TripStopEntity finalStop() { return org.mockito.Mockito.mock(TripStopEntity.class); }
}
