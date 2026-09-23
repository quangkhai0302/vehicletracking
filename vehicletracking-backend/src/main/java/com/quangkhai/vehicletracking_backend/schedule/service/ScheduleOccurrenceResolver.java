package com.quangkhai.vehicletracking_backend.schedule.service;

import com.quangkhai.vehicletracking_backend.schedule.entity.ScheduleFrequency;
import com.quangkhai.vehicletracking_backend.schedule.entity.TripScheduleEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.time.zone.ZoneRules;
import java.util.List;
import java.util.Optional;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

public final class ScheduleOccurrenceResolver {
    private ScheduleOccurrenceResolver() {}

    static ZoneId zone(String value) {
        try { return ZoneId.of(value); }
        catch (DateTimeException ex) { throw new ResponseStatusException(BAD_REQUEST, "Múi giờ không hợp lệ."); }
    }

    public static Optional<Instant> next(TripScheduleEntity schedule, Instant from) {
        if (!schedule.isEnabled()) return Optional.empty();
        ZoneId zone = zone(schedule.getTimezone());
        LocalDate start = max(schedule.getEffectiveFrom(), LocalDateTime.ofInstant(from, zone).toLocalDate());
        LocalDate end = schedule.getEffectiveUntil();
        if (schedule.getFrequency() == ScheduleFrequency.ONCE) {
            LocalDate date = schedule.getScheduledDate();
            if (date == null || date.isBefore(start) || (end != null && date.isAfter(end))) return Optional.empty();
            Instant occurrence = resolve(date, schedule.getDepartureTime(), zone);
            return occurrence.isBefore(from) ? Optional.empty() : Optional.of(occurrence);
        }
        for (LocalDate date = start; end == null || !date.isAfter(end); date = date.plusDays(1)) {
            if ((schedule.getWeekdaysMask() & (1 << (date.getDayOfWeek().getValue() - 1))) == 0) continue;
            Instant occurrence = resolve(date, schedule.getDepartureTime(), zone);
            if (!occurrence.isBefore(from)) return Optional.of(occurrence);
        }
        return Optional.empty();
    }

    static Instant resolve(LocalDate date, LocalTime time, ZoneId zone) {
        LocalDateTime local = LocalDateTime.of(date, time);
        ZoneRules rules = zone.getRules();
        List<ZoneOffset> offsets = rules.getValidOffsets(local);
        if (offsets.isEmpty())
            throw new ResponseStatusException(BAD_REQUEST, "Giờ khởi hành không tồn tại tại múi giờ đã chọn.");
        // During a fall-back overlap, use the earlier offset consistently so one local time maps to one occurrence.
        return local.toInstant(offsets.get(0));
    }

    private static LocalDate max(LocalDate left, LocalDate right) { return left.isAfter(right) ? left : right; }
}
