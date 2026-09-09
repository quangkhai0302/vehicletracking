package com.quangkhai.vehiceltracking_backend.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.quangkhai.vehiceltracking_backend.enums.CheckInStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "trip_checkins")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripCheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    @JsonBackReference
    private Trip trip;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @Column(name = "stop_order", nullable = false)
    private Integer stopOrder;

    @Column(name = "scheduled_arrival_time")
    private LocalDateTime scheduledArrivalTime;

    @Column(name = "actual_arrival_time")
    private LocalDateTime actualArrivalTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CheckInStatus status = CheckInStatus.PENDING;
}
