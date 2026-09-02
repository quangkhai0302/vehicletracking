package com.quangkhai.vehiceltracking_backend.entity;

import com.quangkhai.vehiceltracking_backend.enums.StationType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "stations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Station {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    private String address;

    @Column(name = "radius_meters", nullable = false)
    @Builder.Default
    private Double radiusMeters = 50.0;

    @Enumerated(EnumType.STRING)
    @Column(name = "station_type", nullable = false, length = 20)
    @Builder.Default
    private StationType stationType = StationType.STOP;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (radiusMeters == null) {
            radiusMeters = 50.0;
        }
        if (stationType == null) {
            stationType = StationType.STOP;
        }
    }
}
