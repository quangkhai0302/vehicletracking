package com.quangkhai.vehicletracking_backend.trip.entity;

import com.quangkhai.vehicletracking_backend.route.entity.RouteEntity;
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
    @Column(name = "scheduled_departure_at", nullable = false)
    private Instant scheduledDepartureAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TripStatus status = TripStatus.SCHEDULED;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "ended_at") private Instant endedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNumber ASC")
    private List<TripStopEntity> stops = new ArrayList<>();

    public TripEntity(VehicleEntity vehicle, RouteEntity route, Instant scheduledDepartureAt) {
        this.vehicle = vehicle; this.route = route;
        this.vehiclePlateSnapshot = vehicle.getPlateNumber();
        this.scheduledDepartureAt = scheduledDepartureAt;
    }
    @PrePersist void initializeTimestamp() { createdAt = Instant.now(); }
    public void addStop(TripStopEntity stop) { stops.add(stop); stop.assignTo(this); }
    public void start(Instant now) { status = TripStatus.IN_PROGRESS; startedAt = now; }
    public void complete(Instant now) { status = TripStatus.COMPLETED; endedAt = now; }
    public void cancel(Instant now) { status = TripStatus.CANCELLED; endedAt = now; }
    public void reschedule(Instant departure) {
        scheduledDepartureAt = departure;
        stops.forEach(stop -> stop.reschedule(departure));
    }
}
