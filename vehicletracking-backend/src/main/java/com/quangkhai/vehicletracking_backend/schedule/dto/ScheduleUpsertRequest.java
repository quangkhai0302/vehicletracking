package com.quangkhai.vehicletracking_backend.schedule.dto;

import com.quangkhai.vehicletracking_backend.schedule.entity.ScheduleFrequency;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

public record ScheduleUpsertRequest(
        @Size(max = 150) String name,
        @NotNull @Positive Long routeId,
        @NotNull @Positive Long vehicleId,
        @NotNull @Positive Long driverId,
        @NotNull ScheduleFrequency frequency,
        LocalDate scheduledDate,
        @Min(0) @Max(127) Short weekdaysMask,
        @NotNull LocalTime departureTime,
        @NotBlank @Size(max = 64) String timezone,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveUntil,
        Map<@Min(1) Integer, @NotNull @Min(0) Integer> expectedEmployeeBoardings
) {
    public ScheduleUpsertRequest(String name, Long routeId, Long vehicleId, Long driverId,
            ScheduleFrequency frequency, LocalDate scheduledDate, Short weekdaysMask, LocalTime departureTime,
            String timezone, LocalDate effectiveFrom, LocalDate effectiveUntil) {
        this(name, routeId, vehicleId, driverId, frequency, scheduledDate, weekdaysMask, departureTime,
                timezone, effectiveFrom, effectiveUntil, Map.of());
    }
}
