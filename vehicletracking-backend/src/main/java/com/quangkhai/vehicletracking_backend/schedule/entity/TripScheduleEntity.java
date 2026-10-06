package com.quangkhai.vehicletracking_backend.schedule.entity;

import com.quangkhai.vehicletracking_backend.driver.entity.DriverEntity;
import com.quangkhai.vehicletracking_backend.route.entity.RouteEntity;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "trip_schedules", schema = "vehicle_tracking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripScheduleEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 150) private String name;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "route_id", nullable = false)
    private RouteEntity route;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "vehicle_id", nullable = false)
    private VehicleEntity vehicle;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "driver_id", nullable = false)
    private DriverEntity driver;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12)
    private ScheduleFrequency frequency;
    @Column(name = "scheduled_date") private LocalDate scheduledDate;
    @Column(name = "weekdays_mask", nullable = false) private short weekdaysMask;
    @Column(name = "departure_time", nullable = false) private LocalTime departureTime;
    @Column(nullable = false, length = 64) private String timezone;
    @Column(name = "effective_from", nullable = false) private LocalDate effectiveFrom;
    @Column(name = "effective_until") private LocalDate effectiveUntil;
    @Column(nullable = false) private boolean enabled = true;
    @Column(name = "last_run_at") private Instant lastRunAt;
    @Enumerated(EnumType.STRING) @Column(name = "last_run_status", length = 20)
    private ScheduleRunStatus lastRunStatus;
    @Column(name = "last_run_message", length = 500) private String lastRunMessage;
    @Version
    @Column(nullable = false)
    private long version;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    // Historical columns are retained so existing data remains intact; they no longer gate scheduling.
    @Column(name = "depot_to_origin_seconds", nullable = false) private int depotToOriginSeconds;
    @Column(name = "vehicle_terminal_to_depot_seconds", nullable = false) private int vehicleTerminalToDepotSeconds;
    @Column(name = "driver_terminal_to_depot_seconds", nullable = false) private int driverTerminalToDepotSeconds;
    @Column(name = "preparation_seconds", nullable = false) private int preparationSeconds = 900;
    @Column(name = "execution_kind", nullable = false, length = 16)
    private String executionKind = "REAL";
    @Column(name = "turnaround_configured", nullable = false)
    private boolean turnaroundConfigured;
    @ElementCollection
    @CollectionTable(name = "trip_schedule_stop_boardings", schema = "vehicle_tracking",
            joinColumns = @JoinColumn(name = "schedule_id"))
    @MapKeyColumn(name = "stop_sequence")
    @Column(name = "expected_employee_boarding_count", nullable = false)
    private Map<Integer, Integer> expectedEmployeeBoardings = new HashMap<>();

    public TripScheduleEntity(String name, RouteEntity route, VehicleEntity vehicle, DriverEntity driver,
            ScheduleFrequency frequency, LocalDate scheduledDate, short weekdaysMask, LocalTime departureTime,
            String timezone, LocalDate effectiveFrom, LocalDate effectiveUntil) {
        update(name, route, vehicle, driver, frequency, scheduledDate, weekdaysMask, departureTime, timezone, effectiveFrom, effectiveUntil);
    }

    public TripScheduleEntity(String name, RouteEntity route, VehicleEntity vehicle, DriverEntity driver,
            ScheduleFrequency frequency, LocalDate scheduledDate, short weekdaysMask, LocalTime departureTime,
            String timezone, LocalDate effectiveFrom, LocalDate effectiveUntil, Map<Integer, Integer> boardings) {
        this(name, route, vehicle, driver, frequency, scheduledDate, weekdaysMask, departureTime, timezone, effectiveFrom, effectiveUntil);
        updateExpectedEmployeeBoardings(boardings);
    }

    @PrePersist void initializeTimestamps() { createdAt = Instant.now(); updatedAt = createdAt; }

    public void update(String name, RouteEntity route, VehicleEntity vehicle, DriverEntity driver,
            ScheduleFrequency frequency, LocalDate scheduledDate, short weekdaysMask, LocalTime departureTime,
            String timezone, LocalDate effectiveFrom, LocalDate effectiveUntil) {
        this.name = name;
        this.route = route;
        this.vehicle = vehicle;
        this.driver = driver;
        this.frequency = frequency;
        this.scheduledDate = scheduledDate;
        this.weekdaysMask = weekdaysMask;
        this.departureTime = departureTime;
        this.timezone = timezone;
        this.effectiveFrom = effectiveFrom;
        this.effectiveUntil = effectiveUntil;
        updatedAt = Instant.now();
    }


    public void enable() {
        if (!enabled) { enabled = true; updatedAt = Instant.now(); }
    }
    public void disable() {
        if (enabled) { enabled = false; updatedAt = Instant.now(); }
    }
    public void updateExpectedEmployeeBoardings(Map<Integer, Integer> boardings) {
        expectedEmployeeBoardings.clear();
        if (boardings != null) expectedEmployeeBoardings.putAll(boardings);
        updatedAt = Instant.now();
    }
    public void recordSuccess(Instant at) { lastRunAt = at; lastRunStatus = ScheduleRunStatus.SUCCESS; lastRunMessage = null; updatedAt = Instant.now(); }
    public void recordFailure(Instant at, String message) {
        lastRunAt = at; lastRunStatus = ScheduleRunStatus.FAILED;
        lastRunMessage = message == null ? null : message.substring(0, Math.min(message.length(), 500));
        updatedAt = Instant.now();
    }
}
