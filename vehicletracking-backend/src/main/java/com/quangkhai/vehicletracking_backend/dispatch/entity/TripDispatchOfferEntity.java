package com.quangkhai.vehicletracking_backend.dispatch.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trip_dispatch_offers", schema = "vehicle_tracking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripDispatchOfferEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "trip_id", nullable = false)
    private TripDispatchEntity dispatch;
    @Column(name = "candidate_driver_id", nullable = false) private long candidateDriverId;
    @Column(nullable = false) private short priority;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private DispatchOfferStatus status;
    @Column(name = "dispatch_revision", nullable = false) private long dispatchRevision;
    @Column(name = "offered_at", nullable = false) private Instant offeredAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "responded_at") private Instant respondedAt;
    @Column(name = "response_reason", length = 500) private String responseReason;

    public TripDispatchOfferEntity(TripDispatchEntity dispatch, long candidateId, short priority,
                                   Instant now, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.dispatch = dispatch;
        this.candidateDriverId = candidateId;
        this.priority = priority;
        this.status = DispatchOfferStatus.PENDING;
        this.dispatchRevision = dispatch.getRevision();
        this.offeredAt = now;
        this.expiresAt = expiresAt;
    }

    public void resolve(DispatchOfferStatus result, Instant now, String reason) {
        if (status != DispatchOfferStatus.PENDING) throw new IllegalStateException("Lời mời đã được xử lý.");
        status = result;
        respondedAt = now;
        responseReason = reason;
    }
}
