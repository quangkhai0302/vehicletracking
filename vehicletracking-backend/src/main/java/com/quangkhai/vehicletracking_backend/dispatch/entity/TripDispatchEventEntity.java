package com.quangkhai.vehicletracking_backend.dispatch.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "trip_dispatch_events", schema = "vehicle_tracking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripDispatchEventEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false) private TripDispatchEntity dispatch;
    @Column(nullable = false) private long revision;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private DispatchEventKind kind;
    @Enumerated(EnumType.STRING) @Column(name = "actor_kind", nullable = false, length = 8)
    private DispatchActorKind actorKind;
    @Column(name = "actor_account_id") private Long actorAccountId;
    @Column(name = "actor_driver_id") private Long actorDriverId;
    @Column(name = "from_driver_id") private Long fromDriverId;
    @Column(name = "to_driver_id") private Long toDriverId;
    @Column(length = 500) private String reason;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public TripDispatchEventEntity(TripDispatchEntity dispatch, DispatchEventKind kind, DispatchActorKind actor,
                                   Long accountId, Long driverId, Long fromDriverId, Long toDriverId,
                                   String reason, Instant now) {
        this.dispatch = dispatch;
        revision = dispatch.getRevision();
        this.kind = kind;
        actorKind = actor;
        actorAccountId = accountId;
        actorDriverId = driverId;
        this.fromDriverId = fromDriverId;
        this.toDriverId = toDriverId;
        this.reason = reason;
        createdAt = now;
    }
}
