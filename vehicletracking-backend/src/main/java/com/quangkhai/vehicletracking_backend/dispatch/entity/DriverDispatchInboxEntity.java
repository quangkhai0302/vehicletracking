package com.quangkhai.vehicletracking_backend.dispatch.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "driver_dispatch_inbox", schema = "vehicle_tracking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DriverDispatchInboxEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "recipient_driver_id", nullable = false) private long recipientDriverId;
    @Column(name = "trip_id", nullable = false) private long tripId;
    @Column(name = "offer_id") private UUID offerId;
    @Column(name = "assignment_request_id") private UUID assignmentRequestId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private DriverInboxKind kind;
    @Column(nullable = false, length = 180) private String title;
    @Column(length = 500) private String detail;
    @Column(name = "dedupe_key", nullable = false, length = 255) private String dedupeKey;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "read_at") private Instant readAt;

    public DriverDispatchInboxEntity(long recipientDriverId, long tripId, UUID offerId, DriverInboxKind kind,
                                     String title, String detail, String dedupeKey, Instant now) {
        this(recipientDriverId, tripId, offerId, null, kind, title, detail, dedupeKey, now);
    }

    public DriverDispatchInboxEntity(long recipientDriverId, long tripId, UUID offerId, UUID assignmentRequestId,
                                     DriverInboxKind kind, String title, String detail, String dedupeKey, Instant now) {
        this.recipientDriverId = recipientDriverId;
        this.tripId = tripId;
        this.offerId = offerId;
        this.assignmentRequestId = assignmentRequestId;
        this.kind = kind;
        this.title = title;
        this.detail = detail;
        this.dedupeKey = dedupeKey;
        createdAt = now;
    }

    public void markRead(Instant now) { if (readAt == null) readAt = now; }
}
