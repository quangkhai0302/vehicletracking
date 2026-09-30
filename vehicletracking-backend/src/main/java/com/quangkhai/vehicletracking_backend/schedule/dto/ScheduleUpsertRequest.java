package com.quangkhai.vehicletracking_backend.schedule.dto;

import com.quangkhai.vehicletracking_backend.schedule.entity.ScheduleFrequency;
import com.quangkhai.vehicletracking_backend.dispatch.entity.DispatchStartMode;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

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
        DispatchStartMode startMode,
        Boolean backupEnabled,
        List<@NotNull @Positive Long> backupDriverIds
) {}
