package com.quangkhai.vehicletracking_backend.vehicle.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "vehicles", schema = "vehicle_tracking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VehicleEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "plate_number", nullable = false, length = 20)
    private String plateNumber;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(length = 255)
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType = VehicleType.CAR;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public VehicleEntity(String plateNumber, String name, String description) {
        this(plateNumber, name, description, VehicleType.CAR);
    }
    public VehicleEntity(String plateNumber, String name, String description, VehicleType vehicleType) {
        this.plateNumber = plateNumber;
        this.name = name;
        this.description = description;
        this.vehicleType = vehicleType == null ? VehicleType.CAR : vehicleType;
    }
    @PrePersist
    void initializeTimestamps() { createdAt = Instant.now(); updatedAt = createdAt; }
    public void updateDetails(String plateNumber, String name, String description) {
        updateDetails(plateNumber, name, description, vehicleType);
    }
    public void updateDetails(String plateNumber, String name, String description, VehicleType vehicleType) {
        this.plateNumber = plateNumber; this.name = name; this.description = description;
        this.vehicleType = vehicleType == null ? VehicleType.CAR : vehicleType;
        updatedAt = Instant.now();
    }
    public void deactivate() { active = false; updatedAt = Instant.now(); }
}
