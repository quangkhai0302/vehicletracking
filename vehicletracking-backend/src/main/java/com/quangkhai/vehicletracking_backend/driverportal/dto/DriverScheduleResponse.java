package com.quangkhai.vehicletracking_backend.driverportal.dto;

import com.quangkhai.vehicletracking_backend.dispatch.entity.DispatchStartMode;
import com.quangkhai.vehicletracking_backend.schedule.dto.ScheduleResponse;
import com.quangkhai.vehicletracking_backend.schedule.entity.ScheduleFrequency;
import com.quangkhai.vehicletracking_backend.schedule.entity.ScheduleRunStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record DriverScheduleResponse(
        Long id, String name, Long routeId, String routeName, Long vehicleId, String vehiclePlate,
        Long driverId, String driverName, ScheduleFrequency frequency, LocalDate scheduledDate,
        short weekdaysMask, LocalTime departureTime, String timezone, LocalDate effectiveFrom,
        LocalDate effectiveUntil, boolean enabled, Instant nextRunAt, Instant lastRunAt,
        ScheduleRunStatus lastRunStatus, String lastRunMessage, DispatchStartMode startMode
) {
    public static DriverScheduleResponse from(ScheduleResponse item) {
        return new DriverScheduleResponse(item.id(), item.name(), item.routeId(), item.routeName(),
                item.vehicleId(), item.vehiclePlate(), item.driverId(), item.driverName(), item.frequency(),
                item.scheduledDate(), item.weekdaysMask(), item.departureTime(), item.timezone(),
                item.effectiveFrom(), item.effectiveUntil(), item.enabled(), item.nextRunAt(),
                item.lastRunAt(), item.lastRunStatus(), item.lastRunMessage(), item.startMode());
    }
}
