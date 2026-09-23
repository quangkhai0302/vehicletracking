package com.quangkhai.vehicletracking_backend.schedule.service;

import com.quangkhai.vehicletracking_backend.schedule.entity.ScheduleFrequency;
import com.quangkhai.vehicletracking_backend.schedule.entity.TripScheduleEntity;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

class ScheduleOccurrenceResolverTest {
    @Test
    void findsTheNextSelectedWeeklyWeekdayInTheStoredTimezone() {
        TripScheduleEntity schedule = schedule(ScheduleFrequency.WEEKLY, null, (short) (1 << 2),
                LocalDate.of(2026, 9, 1), null, LocalTime.of(8, 30), "Asia/Ho_Chi_Minh");

        Instant result = ScheduleOccurrenceResolver.next(schedule, Instant.parse("2026-09-21T00:00:00Z")).orElseThrow();

        assertEquals(Instant.parse("2026-09-23T01:30:00Z"), result);
    }

    @Test
    void returnsNoOccurrenceForPastOneOffSchedule() {
        TripScheduleEntity schedule = schedule(ScheduleFrequency.ONCE, LocalDate.of(2026, 9, 20), (short) 0,
                LocalDate.of(2026, 9, 1), null, LocalTime.NOON, "UTC");

        assertTrue(ScheduleOccurrenceResolver.next(schedule, Instant.parse("2026-09-21T00:00:00Z")).isEmpty());
    }

    @Test
    void rejectsLocalTimeInDstGapRatherThanSilentlyMovingIt() {
        TripScheduleEntity schedule = schedule(ScheduleFrequency.ONCE, LocalDate.of(2026, 3, 8), (short) 0,
                LocalDate.of(2026, 3, 1), null, LocalTime.of(2, 30), "America/New_York");

        assertThrows(ResponseStatusException.class,
                () -> ScheduleOccurrenceResolver.next(schedule, Instant.parse("2026-03-01T00:00:00Z")));
    }

    @Test
    void resolvesDstOverlapUsingTheEarlierOffset() {
        TripScheduleEntity schedule = schedule(ScheduleFrequency.ONCE, LocalDate.of(2026, 11, 1), (short) 0,
                LocalDate.of(2026, 10, 1), null, LocalTime.of(1, 30), "America/New_York");

        Instant result = ScheduleOccurrenceResolver.next(schedule, Instant.parse("2026-10-31T00:00:00Z")).orElseThrow();

        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), result);
    }

    private TripScheduleEntity schedule(ScheduleFrequency frequency, LocalDate scheduledDate, short weekdaysMask,
            LocalDate effectiveFrom, LocalDate effectiveUntil, LocalTime time, String timezone) {
        return new TripScheduleEntity("Test", null, null, null, frequency, scheduledDate, weekdaysMask, time, timezone,
                effectiveFrom, effectiveUntil);
    }
}
