package com.quangkhai.vehicletracking_backend.dispatch.entity;

import com.quangkhai.vehicletracking_backend.driver.entity.DriverEntity;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "trip_dispatches", schema = "vehicle_tracking")
@Getter
@org.hibernate.annotations.Immutable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripDispatchEntity {
    @Id private Long tripId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @MapsId
    @JoinColumn(name = "trip_id") private TripEntity trip;
    @Enumerated(EnumType.STRING) @Column(name = "start_mode", nullable = false, length = 20)
    private DispatchStartMode startMode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24)
    private DispatchState state;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "primary_driver_id", nullable = false) private DriverEntity primaryDriver;
    @Column(name = "schedule_epoch", nullable = false) private long scheduleEpoch;
    @Column(name = "baseline_duration_seconds", nullable = false) private long baselineDurationSeconds;
    @Column(name = "assignment_revision", nullable = false) private long assignmentRevision;
    @Column(nullable = false) private long revision;
    @Column(name = "ready_driver_id") private Long readyDriverId;
    @Column(name = "ready_vehicle_id") private Long readyVehicleId;
    @Column(name = "ready_attempt_number") private Integer readyAttemptNumber;
    @Column(name = "ready_assignment_revision") private Long readyAssignmentRevision;
    @Column(name = "ready_at") private Instant readyAt;
    @Enumerated(EnumType.STRING) @Column(name = "attention_code", length = 32)
    private DispatchAttentionCode attentionCode;
    @Column(name = "due_alerted_at") private Instant dueAlertedAt;
    @Column(name = "next_action_at") private Instant nextActionAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
}
