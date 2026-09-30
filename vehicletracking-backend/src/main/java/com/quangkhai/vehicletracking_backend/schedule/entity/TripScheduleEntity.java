package com.quangkhai.vehicletracking_backend.schedule.entity;

import com.quangkhai.vehicletracking_backend.driver.entity.DriverEntity;
import com.quangkhai.vehicletracking_backend.dispatch.entity.DispatchStartMode;
import com.quangkhai.vehicletracking_backend.route.entity.RouteEntity;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.ListIndexBase;
import org.hibernate.annotations.ListIndexJdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
    @Enumerated(EnumType.STRING) @Column(name = "start_mode", nullable = false, length = 20)
    private DispatchStartMode startMode = DispatchStartMode.MANUAL;
    @Column(name = "backup_enabled", nullable = false) private boolean backupEnabled;
    @Column(name = "dispatch_epoch", nullable = false) private long dispatchEpoch;
    @ElementCollection
    @CollectionTable(name = "schedule_backup_drivers", schema = "vehicle_tracking",
            joinColumns = @JoinColumn(name = "schedule_id"))
    @Column(name = "driver_id", nullable = false)
    @OrderColumn(name = "priority")
    @ListIndexBase(1)
    @ListIndexJdbcTypeCode(SqlTypes.SMALLINT)
    private List<Long> backupDriverIds = new ArrayList<>();
    @Column(name = "last_run_at") private Instant lastRunAt;
    @Enumerated(EnumType.STRING) @Column(name = "last_run_status", length = 20)
    private ScheduleRunStatus lastRunStatus;
    @Column(name = "last_run_message", length = 500) private String lastRunMessage;
    @Version
    @Column(nullable = false)
    private long version;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public TripScheduleEntity(String name, RouteEntity route, VehicleEntity vehicle, DriverEntity driver,
            ScheduleFrequency frequency, LocalDate scheduledDate, short weekdaysMask, LocalTime departureTime,
            String timezone, LocalDate effectiveFrom, LocalDate effectiveUntil) {
        update(name, route, vehicle, driver, frequency, scheduledDate, weekdaysMask, departureTime, timezone, effectiveFrom, effectiveUntil);
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

    public void setDispatchPolicy(DispatchStartMode startMode, boolean backupEnabled, List<Long> backupDriverIds) {
        this.startMode = startMode;
        this.backupEnabled = backupEnabled;
        this.backupDriverIds.clear();
        this.backupDriverIds.addAll(backupDriverIds);
        updatedAt = Instant.now();
    }
    public void enable() {
        if (!enabled) { enabled = true; dispatchEpoch++; updatedAt = Instant.now(); }
    }
    public void disable() {
        if (enabled) { enabled = false; dispatchEpoch++; updatedAt = Instant.now(); }
    }
    public void recordSuccess(Instant at) { lastRunAt = at; lastRunStatus = ScheduleRunStatus.SUCCESS; lastRunMessage = null; updatedAt = Instant.now(); }
    public void recordFailure(Instant at, String message) {
        lastRunAt = at; lastRunStatus = ScheduleRunStatus.FAILED;
        lastRunMessage = message == null ? null : message.substring(0, Math.min(message.length(), 500));
        updatedAt = Instant.now();
    }
}
