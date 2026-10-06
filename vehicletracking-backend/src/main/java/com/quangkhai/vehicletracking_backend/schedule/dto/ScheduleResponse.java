package com.quangkhai.vehicletracking_backend.schedule.dto;

import com.quangkhai.vehicletracking_backend.schedule.entity.ScheduleFrequency;
import com.quangkhai.vehicletracking_backend.schedule.entity.ScheduleRunStatus;
import com.quangkhai.vehicletracking_backend.schedule.entity.TripScheduleEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

public record ScheduleResponse(
        Long id, String name, Long routeId, String routeName, Long vehicleId, String vehiclePlate,
        Long driverId, String driverName, ScheduleFrequency frequency, LocalDate scheduledDate,
        short weekdaysMask, LocalTime departureTime, String timezone, LocalDate effectiveFrom,
        LocalDate effectiveUntil, boolean enabled, Instant nextRunAt, Instant lastRunAt,
        ScheduleRunStatus lastRunStatus, String lastRunMessage, Map<Integer, Integer> expectedEmployeeBoardings
) {
    public ScheduleResponse(Long id, String name, Long routeId, String routeName, Long vehicleId, String vehiclePlate,
            Long driverId, String driverName, ScheduleFrequency frequency, LocalDate scheduledDate,
            short weekdaysMask, LocalTime departureTime, String timezone, LocalDate effectiveFrom,
            LocalDate effectiveUntil, boolean enabled, Instant nextRunAt, Instant lastRunAt,
            ScheduleRunStatus lastRunStatus, String lastRunMessage) {
        this(id, name, routeId, routeName, vehicleId, vehiclePlate, driverId, driverName, frequency, scheduledDate,
                weekdaysMask, departureTime, timezone, effectiveFrom, effectiveUntil, enabled, nextRunAt, lastRunAt,
                lastRunStatus, lastRunMessage, Map.of());
    }
    public static ScheduleResponse from(TripScheduleEntity item, Instant nextRunAt) {
        // Preserve stored history while suppressing a blocker from the retired depot policy.
        boolean retiredPolicyFailure = item.getLastRunStatus() == ScheduleRunStatus.FAILED
                && item.getLastRunMessage() != null
                && item.getLastRunMessage().startsWith("TURNAROUND_PLAN_REQUIRED:");
        return new ScheduleResponse(item.getId(), item.getName(), item.getRoute().getId(), item.getRoute().getName(),
                item.getVehicle().getId(), item.getVehicle().getPlateNumber(), item.getDriver().getId(), item.getDriver().getFullName(),
                item.getFrequency(), item.getScheduledDate(), item.getWeekdaysMask(), item.getDepartureTime(), item.getTimezone(),
                item.getEffectiveFrom(), item.getEffectiveUntil(), item.isEnabled(), nextRunAt, item.getLastRunAt(),
                retiredPolicyFailure ? null : item.getLastRunStatus(),
                retiredPolicyFailure ? null : item.getLastRunMessage(), Map.copyOf(item.getExpectedEmployeeBoardings()));
    }
}
