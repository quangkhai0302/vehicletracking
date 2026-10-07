package com.quangkhai.vehicletracking_backend.reporting;

import com.quangkhai.vehicletracking_backend.checkin.entity.CheckInEvidenceKind;
import com.quangkhai.vehicletracking_backend.checkin.entity.TripStopVisitEntity;
import com.quangkhai.vehicletracking_backend.checkin.repository.TripStopVisitRepository;
import com.quangkhai.vehicletracking_backend.config.ReportingProperties;
import com.quangkhai.vehicletracking_backend.driver.entity.DriverEntity;
import com.quangkhai.vehicletracking_backend.reporting.dto.OperationalReportDetailResponse;
import com.quangkhai.vehicletracking_backend.reporting.service.OperationalReportDetailService;
import com.quangkhai.vehicletracking_backend.reporting.service.OperationalReportService;
import com.quangkhai.vehicletracking_backend.reroute.entity.*;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripNotificationRepository;
import com.quangkhai.vehicletracking_backend.route.entity.RouteEntity;
import com.quangkhai.vehicletracking_backend.simulation.entity.*;
import com.quangkhai.vehicletracking_backend.simulation.repository.SimulationRepository;
import com.quangkhai.vehicletracking_backend.telemetry.entity.*;
import com.quangkhai.vehicletracking_backend.telemetry.repository.TelemetryRepository;
import com.quangkhai.vehicletracking_backend.trip.entity.*;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OperationalReportClarityTest {
    private final Instant departure = Instant.parse("2026-10-07T01:00:00Z");
    private final TripRepository trips = mock(TripRepository.class);
    private final TripStopVisitRepository visits = mock(TripStopVisitRepository.class);
    private final TripNotificationRepository notifications = mock(TripNotificationRepository.class);
    private final TelemetryRepository telemetry = mock(TelemetryRepository.class);
    private final SimulationRepository simulations = mock(SimulationRepository.class);
    private final Clock clock = Clock.fixed(departure.plusSeconds(7200), ZoneOffset.UTC);
    private final OperationalReportService summary = new OperationalReportService(trips, telemetry, notifications,
            new ReportingProperties(), clock, simulations);
    private final OperationalReportDetailService service = new OperationalReportDetailService(summary, trips,
            visits, notifications, telemetry, new ReportingProperties(), clock);

    @Test
    void excludesUnstartedTripsAndCancelledBeforeDepartureButKeepsStartedCancellation() {
        TripEntity planned = trip(1, 10);
        TripEntity cancelledBeforeStart = trip(2, 10);
        cancelledBeforeStart.cancel(departure);
        TripEntity cancelledAfterStart = trip(3, 10);
        cancelledAfterStart.start(departure);
        cancelledAfterStart.cancel(departure.plusSeconds(300));
        load(List.of(planned, cancelledBeforeStart, cancelledAfterStart), List.of());
        var report = report();
        assertThat(report.summary().tripCount()).isEqualTo(1);
        assertThat(report.summary().completedTripCount()).isZero();
        assertThat(report.vehicles()).singleElement().satisfies(row -> assertThat(row.tripCount()).isEqualTo(1));
        assertThat(report.drivers()).singleElement().satisfies(row -> assertThat(row.tripCount()).isEqualTo(1));
    }

    @Test
    void countsConfirmedPassengersWithoutSegmentTimesAndUsesFrozenSeatsNotCurrentConfiguration() {
        TripEntity trip = trip(1, 10);
        trip.start(departure);
        trip.complete(departure.plusSeconds(100));
        trip.getVehicle().updateSeatCapacity(20);
        load(List.of(trip), List.of(visit(trip, 1, 2, null), visit(trip, 2, 3, null), visit(trip, 3, null, null)));
        var report = report();
        assertThat(report.employeeOccupancy().totalBoardings()).isEqualTo(5);
        assertThat(report.employeeOccupancy().averageBoardingsPerTrip()).isEqualTo(5d);
        assertThat(report.employeeOccupancy().seatUtilizationPercent()).isEqualTo(50d);
        assertThat(report.employeeOccupancy().averageOnboard()).isNull();
        assertThat(report.employeeOccupancyByVehicle()).singleElement().satisfies(row -> {
            assertThat(row.seatCapacity()).isEqualTo(20);
            assertThat(row.seatUtilizationPercent()).isEqualTo(50d);
        });
        trip.replay(departure.plusSeconds(1000));
        assertThat(trip.getSeatCapacitySnapshot()).isNull();
        trip.start(departure.plusSeconds(1000));
        assertThat(trip.getSeatCapacitySnapshot()).isEqualTo(20);
    }

    @Test
    void calculatesMixedCapacitiesOnSamePopulationAndKeepsZeroDistinctFromMissingCount() {
        TripEntity tenSeats = completed(1, 10), twentySeats = completed(2, 20);
        TripEntity legacy = completed(3, 20), empty = completed(4, 10), missing = completed(5, 10);
        ReflectionTestUtils.setField(legacy, "seatCapacitySnapshot", null);
        List<TripStopVisitEntity> rows = new ArrayList<>();
        for (var entry : Map.of(tenSeats, 5, twentySeats, 15, legacy, 8, empty, 0).entrySet()) {
            rows.add(visit(entry.getKey(), 1, entry.getValue(), null));
            rows.add(visit(entry.getKey(), 2, 0, null));
            rows.add(visit(entry.getKey(), 3, null, null));
        }
        rows.add(visit(missing, 1, null, null));
        rows.add(visit(missing, 2, 0, null));
        rows.add(visit(missing, 3, null, null));
        load(List.of(tenSeats, twentySeats, legacy, empty, missing), rows);
        var report = report();
        assertThat(report.employeeOccupancy().totalBoardings()).isEqualTo(28);
        assertThat(report.employeeOccupancy().averageBoardingsPerTrip()).isEqualTo(7d);
        assertThat(report.employeeOccupancy().seatUtilizationPercent()).isEqualTo(50d); // 20 / (10+20+10)
        assertThat(report.employeeOccupancy().tripsMissingBoardingData()).isEqualTo(1);
        assertThat(report.employeeOccupancy().tripsMissingSeatCapacity()).isEqualTo(1);
        assertThat(report.vehicles().stream().filter(row -> row.vehicleId() == 4).findFirst().orElseThrow()
                .employeePassengerCount()).isZero();
        assertThat(report.vehicles().stream().filter(row -> row.vehicleId() == 5).findFirst().orElseThrow()
                .employeePassengerCount()).isNull();
    }

    @Test
    void ignoresRoutineNotificationsAndAdminIncidentsAndReportsRealDriverIncidentStatus() {
        TripEntity trip = completed(1, 10);
        DriverEntity driver = new DriverEntity("Tài xế A", "0000000000", "TEST-A");
        trip.assignDriver(driver);
        var driverIncident = new SimulationIncidentEntity(trip, driver, 1, SimulationIncidentType.VEHICLE_BREAKDOWN,
                NotificationSeverity.CRITICAL, "Hỏng máy", 10, 106, 60, UUID.randomUUID(), departure);
        driverIncident.resolve(departure.plusSeconds(100));
        var driverNotice = notification(trip, NotificationType.SIMULATION_INCIDENT);
        driverNotice.attachSimulationIncident(driverIncident);
        var adminIncident = new SimulationIncidentEntity(trip, null, 1, SimulationIncidentType.OTHER,
                NotificationSeverity.MAJOR, null, 10, 106, 60, UUID.randomUUID(), departure);
        var adminNotice = notification(trip, NotificationType.SIMULATION_INCIDENT);
        adminNotice.attachSimulationIncident(adminIncident);
        var oldOffRoute = notification(trip, NotificationType.OFF_ROUTE_DETECTED);
        oldOffRoute.attributeSimulation(2);
        load(List.of(trip), List.of());
        when(notifications.findAllForOperationalReport(anyList(), any(), any())).thenReturn(List.of(
                notification(trip, NotificationType.DIRECT_ASSIGNMENT_ACCEPTED),
                notification(trip, NotificationType.DIRECT_ASSIGNMENT_DECLINED),
                notification(trip, NotificationType.REROUTE_CREATED),
                notification(trip, NotificationType.REROUTE_UNAVAILABLE),
                notification(trip, NotificationType.DRIVER_ROUTE_CHANGED),
                notification(trip, NotificationType.DISPATCH_ATTENTION),
                notification(trip, NotificationType.DISPATCH_REASSIGNED),
                driverNotice, adminNotice, oldOffRoute, notification(trip, NotificationType.OFF_ROUTE_DETECTED)));
        var report = report();
        assertThat(report.incidentDetails()).hasSize(2);
        assertThat(report.incidentDetails().stream().filter(row -> row.type().equals("VEHICLE_BREAKDOWN")))
                .singleElement().satisfies(row -> {
                    assertThat(row.status()).isEqualTo("RESOLVED");
                    assertThat(row.driverName()).isEqualTo("Tài xế A");
                    assertThat(row.vehiclePlateNumber()).isEqualTo("TEST-1");
                    assertThat(row.detail()).isEqualTo("Hỏng máy");
                });
        assertThat(report.incidents().stream().mapToLong(row -> row.count()).sum()).isEqualTo(2);
        assertThat(report.vehicles()).singleElement().satisfies(row -> assertThat(row.incidentCount()).isEqualTo(2));
    }

    @Test
    void countsSimulatorSpeedEpisodesAndUsesSimulatorArrivalsInsteadOfWallClockForLateStops() {
        TripEntity trip = completed(1, 10);
        load(List.of(trip), List.of(visit(trip, 1, 0, departure),
                visit(trip, 2, 0, departure.plusSeconds(330)), visit(trip, 3, null, departure.plusSeconds(600))));
        List<TelemetrySampleEntity> samples = new ArrayList<>();
        int index = 0;
        for (double speed : List.of(70d, 90d, 95d, 70d, 81d)) {
            var sample = mock(TelemetrySampleEntity.class);
            when(sample.getId()).thenReturn((long) ++index);
            when(sample.getTripId()).thenReturn(1L);
            when(sample.getAttemptNumber()).thenReturn(1);
            when(sample.getSource()).thenReturn(TelemetrySource.SIMULATOR);
            when(sample.getSpeedKmh()).thenReturn(speed);
            when(sample.getRecordedAt()).thenReturn(departure.plusSeconds(index));
            samples.add(sample);
        }
        when(telemetry.findAllForOperationalReport(anyList(), any())).thenReturn(samples);
        var report = report();
        assertThat(report.summary().overspeedEventCount()).isEqualTo(2);
        assertThat(report.incidentDetails()).hasSize(2);
        assertThat(report.lateStops()).singleElement().satisfies(row -> {
            assertThat(row.delaySeconds()).isEqualTo(30);
            assertThat(row.actualArrivalAt()).isEqualTo(departure.plusSeconds(330));
        });
    }

    @Test
    void scheduledPunctualityUsesVirtualElapsedTimeInsteadOfFastPlaybackWallTime() {
        TripEntity trip = completed(1, 10);
        ReflectionTestUtils.setField(trip, "schedule", mock(com.quangkhai.vehicletracking_backend.schedule.entity.TripScheduleEntity.class));
        SimulationRunEntity run = new SimulationRunEntity(1, departure);
        run.captureFirstPlay(SimulationAttemptMetadata.capture(trip, departure, 600, 1000));
        run.addVirtualSeconds(601);
        load(List.of(trip), List.of());
        when(simulations.findAllByTripIdIn(anyList())).thenReturn(List.of(run));
        assertThat(report().summary().lateTripCount()).isEqualTo(1);
        assertThat(report().vehicles()).singleElement().satisfies(row -> assertThat(row.lateTripCount()).isEqualTo(1));
    }

    private TripEntity completed(long id, int seats) {
        TripEntity trip = trip(id, seats); trip.start(departure); trip.complete(departure.plusSeconds(600)); return trip;
    }
    private TripEntity trip(long id, int seats) {
        VehicleEntity vehicle = new VehicleEntity("TEST-" + id, "Xe " + id, null);
        vehicle.updateSeatCapacity(seats); ReflectionTestUtils.setField(vehicle, "id", id);
        RouteEntity route = BeanUtils.instantiateClass(RouteEntity.class);
        ReflectionTestUtils.setField(route, "name", "Tuyến trường học");
        TripEntity trip = new TripEntity(vehicle, route, departure);
        ReflectionTestUtils.setField(trip, "id", id);
        for (int sequence = 1; sequence <= 3; sequence++) {
            TripStopEntity stop = BeanUtils.instantiateClass(TripStopEntity.class);
            ReflectionTestUtils.setField(stop, "sequenceNumber", sequence);
            ReflectionTestUtils.setField(stop, "stationName", "Trạm " + sequence);
            ReflectionTestUtils.setField(stop, "arrivalOffsetSeconds", (sequence - 1) * 300L);
            ReflectionTestUtils.setField(stop, "departureOffsetSeconds", (sequence - 1) * 300L);
            trip.addStop(stop);
        }
        return trip;
    }
    private TripStopVisitEntity visit(TripEntity trip, int sequence, Integer count, Instant simulated) {
        var visit = new TripStopVisitEntity(trip, sequence, TelemetrySource.SIMULATOR, CheckInEvidenceKind.SEGMENT,
                departure.plusSeconds(7200), simulated, departure, null, null, 0, 10, 106);
        if (count != null) visit.confirmEmployeeBoarding(count);
        return visit;
    }
    private TripNotificationEntity notification(TripEntity trip, NotificationType type) {
        return new TripNotificationEntity(trip, null, type, NotificationSeverity.MAJOR, "Test", "Test", null,
                "", null, null, UUID.randomUUID().toString(), departure);
    }
    private void load(List<TripEntity> tripRows, List<TripStopVisitEntity> stopRows) {
        when(trips.findAllForOperationalReport(any(), any(), isNull(), isNull())).thenReturn(tripRows);
        when(visits.findAllByTripIdInOrderByTripIdAscStopSequenceAsc(anyList())).thenReturn(stopRows);
    }
    private OperationalReportDetailResponse report() {
        return service.detail(LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 7), null, null);
    }
}
