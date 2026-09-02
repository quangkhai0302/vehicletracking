package com.quangkhai.vehiceltracking_backend.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "route_stations", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"route_id", "stop_order"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    @JsonBackReference
    private Route route;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @Column(name = "stop_order", nullable = false)
    private Integer stopOrder;

    @Column(name = "distance_to_next_km")
    @Builder.Default
    private Double distanceToNextKm = 0.0;

    @Column(name = "estimated_time_to_next_minutes")
    @Builder.Default
    private Double estimatedTimeToNextMinutes = 0.0;
}
