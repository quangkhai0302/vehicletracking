package com.quangkhai.vehicletracking_backend.dispatch.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "driver_unavailability", schema = "vehicle_tracking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DriverUnavailabilityEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "source_trip_id", nullable = false) private long sourceTripId;
    @Column(name = "driver_id", nullable = false) private long driverId;
    @Column(name = "assignment_revision", nullable = false) private long assignmentRevision;
    @Column(name = "starts_at", nullable = false) private Instant startsAt;
    @Column(name = "ends_at", nullable = false) private Instant endsAt;
    @Column(nullable = false, length = 500) private String reason;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    public DriverUnavailabilityEntity(TripDispatchEntity dispatch, long driverId, String reason, Instant now) {
        sourceTripId = dispatch.getTripId();
        this.driverId = driverId;
        assignmentRevision = dispatch.getAssignmentRevision();
        startsAt = dispatch.getTrip().getScheduledDepartureAt().minusSeconds(15 * 60);
        endsAt = dispatch.getTrip().getScheduledDepartureAt()
                .plusSeconds(dispatch.getBaselineDurationSeconds() + 15 * 60);
        this.reason = reason;
        createdAt = now;
    }
}
