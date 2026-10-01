package com.quangkhai.vehicletracking_backend.assignment.entity;

import com.quangkhai.vehicletracking_backend.auth.entity.UserAccountEntity;
import com.quangkhai.vehicletracking_backend.driver.entity.DriverEntity;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trip_assignment_requests", schema = "vehicle_tracking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripAssignmentRequestEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private TripEntity trip;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_driver_id", nullable = false)
    private DriverEntity candidateDriver;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_account_id", nullable = false)
    private UserAccountEntity requestedByAccount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private TripAssignmentStatus status;
    @Column(name = "requested_at", nullable = false) private Instant requestedAt;
    @Column(name = "responded_at") private Instant respondedAt;
    @Column(name = "response_reason", length = 500) private String responseReason;

    public TripAssignmentRequestEntity(TripEntity trip, DriverEntity candidateDriver,
                                       UserAccountEntity requestedByAccount, Instant requestedAt) {
        this.id = UUID.randomUUID();
        this.trip = trip;
        this.candidateDriver = candidateDriver;
        this.requestedByAccount = requestedByAccount;
        this.status = TripAssignmentStatus.PENDING;
        this.requestedAt = requestedAt;
    }

    public void accept(Instant now) { terminal(TripAssignmentStatus.ACCEPTED, now, null); }
    public void decline(Instant now, String reason) { terminal(TripAssignmentStatus.DECLINED, now, reason); }
    public void cancel(Instant now, String reason) { terminal(TripAssignmentStatus.CANCELLED, now, reason); }
    private void terminal(TripAssignmentStatus next, Instant now, String reason) {
        if (status != TripAssignmentStatus.PENDING) throw new IllegalStateException("Yêu cầu đã được xử lý.");
        status = next; respondedAt = now; responseReason = reason;
    }
}
