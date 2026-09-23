package com.quangkhai.vehicletracking_backend.schedule.service;

import com.quangkhai.vehicletracking_backend.schedule.entity.TripScheduleEntity;
import com.quangkhai.vehicletracking_backend.schedule.repository.TripScheduleRepository;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.trip.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;

/** Generates immutable trip snapshots. Database uniqueness is the final idempotency guard. */
@Service
@RequiredArgsConstructor
public class TripScheduleGenerationService {
    private static final int HORIZON_DAYS = 7;
    private final TripScheduleRepository schedules;
    private final TripRepository tripRepository;
    private final TripService trips;
    private final Clock operationsClock;

    @Transactional
    public void generateUpcomingTrips() {
        Instant now = operationsClock.instant();
        Instant horizon = now.plusSeconds(HORIZON_DAYS * 24L * 60L * 60L);
        for (TripScheduleEntity schedule : schedules.findAllByEnabledTrue()) {
            generate(schedule, now, horizon);
        }
    }

    private void generate(TripScheduleEntity schedule, Instant now, Instant horizon) {
        Instant cursor = now;
        while (true) {
            Instant occurrence;
            try {
                occurrence = ScheduleOccurrenceResolver.next(schedule, cursor).orElse(null);
            } catch (ResponseStatusException ex) {
                schedule.recordFailure(now, ex.getReason());
                return;
            }
            if (occurrence == null || occurrence.isAfter(horizon)) return;
            try {
                if (tripRepository.existsByScheduleIdAndScheduleOccurrenceAt(schedule.getId(), occurrence)) {
                    schedule.recordSuccess(now);
                    cursor = occurrence.plusNanos(1_000);
                    continue;
                }
                trips.createFromSchedule(schedule, occurrence);
                schedule.recordSuccess(now);
            } catch (DataIntegrityViolationException ex) {
                if (isUniqueViolation(ex)) {
                    // A second node or a retry already created this occurrence; it is a successful idempotent outcome.
                    schedule.recordSuccess(now);
                } else {
                    schedule.recordFailure(now, "Không thể lưu chuyến tự động.");
                }
            } catch (ResponseStatusException ex) {
                schedule.recordFailure(now, ex.getReason());
            } catch (RuntimeException ex) {
                schedule.recordFailure(now, "Không thể tạo chuyến tự động.");
            }
            cursor = occurrence.plusNanos(1_000);
        }
    }

    private boolean isUniqueViolation(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof SQLException sql && "23505".equals(sql.getSQLState())) return true;
        }
        return false;
    }
}
