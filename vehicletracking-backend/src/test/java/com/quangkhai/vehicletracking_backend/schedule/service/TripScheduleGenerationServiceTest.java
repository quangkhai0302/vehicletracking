package com.quangkhai.vehicletracking_backend.schedule.service;

import com.quangkhai.vehicletracking_backend.driver.entity.DriverEntity;
import com.quangkhai.vehicletracking_backend.schedule.dto.ScheduleResponse;
import com.quangkhai.vehicletracking_backend.schedule.entity.ScheduleFrequency;
import com.quangkhai.vehicletracking_backend.schedule.entity.ScheduleRunStatus;
import com.quangkhai.vehicletracking_backend.schedule.entity.TripScheduleEntity;
import com.quangkhai.vehicletracking_backend.schedule.repository.TripScheduleRepository;
import com.quangkhai.vehicletracking_backend.trip.TripFixtures;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.trip.service.TripService;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripScheduleGenerationServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant OCCURRENCE = Instant.parse("2026-10-05T12:00:00Z");

    @Mock TripScheduleRepository schedules;
    @Mock TripRepository tripsRepository;
    @Mock TripService trips;

    private TripScheduleEntity legacySchedule() {
        var route = TripFixtures.route(TripFixtures.station("A"), TripFixtures.station("B"));
        var vehicle = new VehicleEntity("LEGACY-SCHEDULE", "Xe cũ", null);
        var driver = new DriverEntity("Tài xế", "0900000001", "LEGACY-LIC");
        var schedule = new TripScheduleEntity("Legacy", route, vehicle, driver,
                ScheduleFrequency.WEEKLY, null, (short) 1, LocalTime.NOON, "UTC",
                LocalDate.of(2026, 1, 1), null);
        ReflectionTestUtils.setField(schedule, "id", 1L);
        return schedule;
    }

    private void generate(TripScheduleEntity schedule) {
        when(schedules.findAllByEnabledTrue()).thenReturn(List.of(schedule));
        new TripScheduleGenerationService(schedules, tripsRepository, trips,
                Clock.fixed(NOW, ZoneOffset.UTC)).generateUpcomingTrips();
    }

    @Test void legacyScheduleGeneratesWithoutDepotOrTurnaroundConfiguration() {
        var schedule = legacySchedule();
        schedule.recordFailure(NOW.minusSeconds(60), "TURNAROUND_PLAN_REQUIRED: lịch legacy chưa có cấu hình chuyển tiếp (UNKNOWN).");

        generate(schedule);

        assertThat(schedule.isTurnaroundConfigured()).isFalse();
        verify(trips).createFromSchedule(schedule, OCCURRENCE);
        assertThat(schedule.getLastRunStatus()).isEqualTo(ScheduleRunStatus.SUCCESS);
        assertThat(schedule.getLastRunMessage()).isNull();
    }

    @Test void existingLegacyOccurrenceRemainsIdempotent() {
        var schedule = legacySchedule();
        when(tripsRepository.existsByScheduleIdAndScheduleOccurrenceAt(1L, OCCURRENCE)).thenReturn(true);

        generate(schedule);

        verifyNoInteractions(trips);
        assertThat(schedule.getLastRunStatus()).isEqualTo(ScheduleRunStatus.SUCCESS);
    }

    @Test void schedulingConflictsStillSurfaceAfterDepotRemoval() {
        var schedule = legacySchedule();
        when(trips.createFromSchedule(schedule, OCCURRENCE)).thenThrow(
                new ResponseStatusException(HttpStatus.CONFLICT, "Xe đã có lịch chạy cố định bị chồng thời gian."));

        generate(schedule);

        assertThat(schedule.getLastRunStatus()).isEqualTo(ScheduleRunStatus.FAILED);
        assertThat(ScheduleResponse.from(schedule, null).lastRunMessage()).contains("chồng thời gian");
    }

    @Test void responseHidesRetiredPolicyFailureWithoutChangingStoredHistory() {
        var schedule = legacySchedule();
        String oldMessage = "TURNAROUND_PLAN_REQUIRED: lịch legacy chưa có cấu hình chuyển tiếp (UNKNOWN).";
        schedule.recordFailure(NOW.minusSeconds(60), oldMessage);

        var response = ScheduleResponse.from(schedule, null);

        assertThat(response.lastRunStatus()).isNull();
        assertThat(response.lastRunMessage()).isNull();
        assertThat(schedule.getLastRunStatus()).isEqualTo(ScheduleRunStatus.FAILED);
        assertThat(schedule.getLastRunMessage()).isEqualTo(oldMessage);
    }
}
