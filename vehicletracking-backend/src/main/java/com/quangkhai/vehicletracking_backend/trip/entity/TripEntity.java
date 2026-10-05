package com.quangkhai.vehicletracking_backend.trip.entity;

import com.quangkhai.vehicletracking_backend.driver.entity.DriverEntity;
import com.quangkhai.vehicletracking_backend.route.entity.RouteEntity;
import com.quangkhai.vehicletracking_backend.schedule.entity.TripScheduleEntity;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "trips", schema = "vehicle_tracking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private VehicleEntity vehicle;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private RouteEntity route;
    @Column(name = "vehicle_plate_snapshot", nullable = false, length = 20)
    private String vehiclePlateSnapshot;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private DriverEntity driver;
    @Column(name = "driver_name_snapshot", length = 100)
    private String driverNameSnapshot;
    @Column(name = "driver_phone_snapshot", length = 20)
    private String driverPhoneSnapshot;
    @Column(name = "driver_license_number_snapshot", length = 50)
    private String driverLicenseNumberSnapshot;
    @Column(name = "scheduled_departure_at", nullable = false)
    private Instant scheduledDepartureAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id")
    private TripScheduleEntity schedule;
    @Column(name = "schedule_occurrence_at")
    private Instant scheduleOccurrenceAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TripStatus status = TripStatus.SCHEDULED;
    @Column(name = "attempt_number", nullable = false) private int attemptNumber = 1;
    /** Historical marker retained after the turnaround policy was removed. */
    @Column(name = "turnaround_legacy_max_attempt", nullable = false) private int turnaroundLegacyMaxAttempt = 0;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "ended_at") private Instant endedAt;
    @Column(name = "cancellation_reason", length = 500) private String cancellationReason;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNumber ASC")
    private List<TripStopEntity> stops = new ArrayList<>();

    public TripEntity(VehicleEntity vehicle, RouteEntity route, Instant scheduledDepartureAt) {
        this(vehicle, route, scheduledDepartureAt, null);
    }
    public TripEntity(VehicleEntity vehicle, RouteEntity route, Instant scheduledDepartureAt, DriverEntity driver) {
        this(vehicle, route, scheduledDepartureAt, driver, null, null);
    }
    public TripEntity(VehicleEntity vehicle, RouteEntity route, Instant scheduledDepartureAt, DriverEntity driver,
            TripScheduleEntity schedule, Instant scheduleOccurrenceAt) {
        this.vehicle = vehicle; this.route = route;
        this.vehiclePlateSnapshot = vehicle.getPlateNumber();
        this.scheduledDepartureAt = scheduledDepartureAt;
        this.schedule = schedule;
        this.scheduleOccurrenceAt = scheduleOccurrenceAt;
        assignDriver(driver);
    }
    @PrePersist void initializeTimestamp() { createdAt = Instant.now(); }
    public void addStop(TripStopEntity stop) { stops.add(stop); stop.assignTo(this); }
    public void start(Instant now) { status = TripStatus.IN_PROGRESS; startedAt = now; }
    public void complete(Instant now) { status = TripStatus.COMPLETED; endedAt = now; }
    public void cancel(Instant now) { cancel(now, "Hủy chuyến theo yêu cầu điều phối."); }
    public void cancel(Instant now, String reason) {
        status = TripStatus.CANCELLED;
        endedAt = now;
        cancellationReason = reason == null ? null : reason.trim();
    }
    public void replay(Instant departure) {
        attemptNumber = Math.incrementExact(attemptNumber);
        status = TripStatus.SCHEDULED; startedAt = null; endedAt = null; cancellationReason = null;
        if (schedule == null) reschedule(departure);
    }
    public void reschedule(Instant departure) {
        scheduledDepartureAt = departure;
        stops.forEach(stop -> stop.reschedule(departure));
    }
    /**
     * The virtual clock belongs to the current execution attempt, not to the
     * planning timestamp. Before an attempt starts, the planned timestamp is
     * the only available preview anchor.
     */
    public Instant simulationOriginAt() {
        return startedAt == null ? scheduledDepartureAt : startedAt;
    }
    public void assignVehicle(VehicleEntity vehicle) {
        this.vehicle = vehicle;
        vehiclePlateSnapshot = vehicle.getPlateNumber();
    }
    public void assignDriver(DriverEntity driver) {
        this.driver = driver;
        driverNameSnapshot = driver == null ? null : driver.getFullName();
        driverPhoneSnapshot = driver == null ? null : driver.getPhoneNumber();
        driverLicenseNumberSnapshot = driver == null ? null : driver.getLicenseNumber();
    }
}
