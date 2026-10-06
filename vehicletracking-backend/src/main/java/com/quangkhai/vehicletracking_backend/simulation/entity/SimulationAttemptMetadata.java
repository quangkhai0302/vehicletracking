package com.quangkhai.vehicletracking_backend.simulation.entity;

import java.time.Instant;

import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Frozen first-play identity and free-flow baseline; null fields denote legacy unknowns. */
@Embeddable @Getter @NoArgsConstructor
public class SimulationAttemptMetadata {
    @Column(name="attempt_started_at") private Instant attemptStartedAt;
    @Column(name="planned_duration_seconds") private Double plannedDurationSeconds;
    @Column(name="planned_distance_meters") private Double plannedDistanceMeters;
    @Column(name="report_vehicle_id") private Long vehicleId;
    @Column(name="report_vehicle_plate", length=20) private String vehiclePlateNumber;
    @Column(name="report_driver_id") private Long driverId;
    @Column(name="report_driver_name", length=100) private String driverName;
    @Column(name="report_route_id") private Long routeId;
    @Column(name="report_route_name", length=150) private String routeName;

    public static SimulationAttemptMetadata capture(TripEntity trip, Instant now, double duration, double distance) {
        var result = new SimulationAttemptMetadata();
        result.attemptStartedAt=now; result.plannedDurationSeconds=duration; result.plannedDistanceMeters=distance;
        result.vehicleId=trip.getVehicle().getId(); result.vehiclePlateNumber=trip.getVehiclePlateSnapshot();
        result.driverId=trip.getDriver()==null?null:trip.getDriver().getId(); result.driverName=trip.getDriverNameSnapshot();
        result.routeId=trip.getRoute().getId(); result.routeName=trip.getRoute().getName();
        return result;
    }
    public SimulationAttemptMetadata copy() {
        var result = new SimulationAttemptMetadata();
        result.attemptStartedAt=attemptStartedAt; result.plannedDurationSeconds=plannedDurationSeconds;
        result.plannedDistanceMeters=plannedDistanceMeters; result.vehicleId=vehicleId;
        result.vehiclePlateNumber=vehiclePlateNumber; result.driverId=driverId; result.driverName=driverName;
        result.routeId=routeId; result.routeName=routeName;
        return result;
    }
}
