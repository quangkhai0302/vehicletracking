package com.quangkhai.vehicletracking_backend.driver.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Basic(fetch = FetchType.LAZY)
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "avatar_data", columnDefinition = "bytea")
    private byte[] avatarData;

    @Column(name = "avatar_content_type", length = 50)
    private String avatarContentType;

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

    public void updateAvatar(byte[] data, String contentType) {
        avatarData = data == null ? null : data.clone();
        avatarContentType = contentType;
        updatedAt = Instant.now();
    }

    public void clearAvatar() {
        avatarData = null;
        avatarContentType = null;
        updatedAt = Instant.now();
    }

    public byte[] avatarDataCopy() {
        return avatarData == null ? null : avatarData.clone();
    }

    public boolean hasAvatar() {
        return avatarData != null && avatarData.length > 0 && avatarContentType != null;
    }

    public String avatarContentType() {
        return avatarContentType;
    }
}
