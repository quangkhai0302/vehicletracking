package com.quangkhai.vehicletracking_backend.schedule.dto;

import com.quangkhai.vehicletracking_backend.schedule.entity.ScheduleFrequency;
import com.quangkhai.vehicletracking_backend.schedule.entity.ScheduleRunStatus;
import com.quangkhai.vehicletracking_backend.schedule.entity.TripScheduleEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record ScheduleResponse(
        Long id, String name, Long routeId, String routeName, Long vehicleId, String vehiclePlate,
        Long driverId, String driverName, ScheduleFrequency frequency, LocalDate scheduledDate,
        short weekdaysMask, LocalTime departureTime, String timezone, LocalDate effectiveFrom,
        LocalDate effectiveUntil, boolean enabled, Instant nextRunAt, Instant lastRunAt,
        ScheduleRunStatus lastRunStatus, String lastRunMessage
) {
    public static ScheduleResponse from(TripScheduleEntity item, Instant nextRunAt) {
        return new ScheduleResponse(item.getId(), item.getName(), item.getRoute().getId(), item.getRoute().getName(),
                item.getVehicle().getId(), item.getVehicle().getPlateNumber(), item.getDriver().getId(), item.getDriver().getFullName(),
                item.getFrequency(), item.getScheduledDate(), item.getWeekdaysMask(), item.getDepartureTime(), item.getTimezone(),
                item.getEffectiveFrom(), item.getEffectiveUntil(), item.isEnabled(), nextRunAt, item.getLastRunAt(),
                item.getLastRunStatus(), item.getLastRunMessage());
    }
}
