package com.quangkhai.vehiceltracking_backend.entity;

import com.quangkhai.vehiceltracking_backend.enums.IncidentType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "traffic_incidents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrafficIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private IncidentType type = IncidentType.CONGESTION;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "radius_meters", nullable = false)
    @Builder.Default
    private Double radiusMeters = 200.0;

    @Column(name = "speed_reduction_percent", nullable = false)
    @Builder.Default
    private Double speedReductionPercent = 60.0; // 60% reduction in speed

    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (active == null) {
            active = true;
        }
        if (radiusMeters == null) {
            radiusMeters = 200.0;
        }
        if (speedReductionPercent == null) {
            speedReductionPercent = 60.0;
        }
    }
}
