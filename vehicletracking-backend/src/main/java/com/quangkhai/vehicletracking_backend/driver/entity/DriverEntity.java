package com.quangkhai.vehicletracking_backend.driver.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "drivers", schema = "vehicle_tracking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DriverEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "license_number", nullable = false, length = 50)
    private String licenseNumber;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public DriverEntity(String fullName, String phoneNumber, String licenseNumber) {
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.licenseNumber = licenseNumber;
    }

    @PrePersist
    void initializeTimestamps() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    public void updateDetails(String fullName, String phoneNumber, String licenseNumber) {
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.licenseNumber = licenseNumber;
        updatedAt = Instant.now();
    }

    public void deactivate() {
        active = false;
        updatedAt = Instant.now();
    }
}
