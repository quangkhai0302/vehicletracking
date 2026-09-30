package com.quangkhai.vehicletracking_backend.dispatch.entity;

import com.quangkhai.vehicletracking_backend.driver.entity.DriverEntity;
import com.quangkhai.vehicletracking_backend.schedule.entity.TripScheduleEntity;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ListIndexBase;
import org.hibernate.annotations.ListIndexJdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "trip_dispatches", schema = "vehicle_tracking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripDispatchEntity {
    @Id private Long tripId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @MapsId
    @JoinColumn(name = "trip_id") private TripEntity trip;
    @Enumerated(EnumType.STRING) @Column(name = "start_mode", nullable = false, length = 20)
    private DispatchStartMode startMode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24)
    private DispatchState state;
    @Column(name = "backup_enabled", nullable = false) private boolean backupEnabled;
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
    @ElementCollection
    @CollectionTable(name = "trip_dispatch_candidates", schema = "vehicle_tracking",
            joinColumns = @JoinColumn(name = "trip_id"))
    @Column(name = "driver_id", nullable = false)
    @OrderColumn(name = "priority")
    @ListIndexBase(1)
    @ListIndexJdbcTypeCode(SqlTypes.SMALLINT)
    private List<Long> backupDriverIds = new ArrayList<>();

    public TripDispatchEntity(TripEntity trip, TripScheduleEntity schedule, long baselineDurationSeconds, Instant now) {
        this.trip = trip;
        this.startMode = schedule.getStartMode();
        this.state = startMode == DispatchStartMode.MANUAL ? DispatchState.MANUAL : DispatchState.WAITING_READY;
        this.backupEnabled = schedule.isBackupEnabled();
        this.primaryDriver = schedule.getDriver();
        this.scheduleEpoch = schedule.getDispatchEpoch();
        this.baselineDurationSeconds = Math.max(1, baselineDurationSeconds);
        this.backupDriverIds.addAll(schedule.getBackupDriverIds());
        this.createdAt = now;
        this.updatedAt = now;
        this.nextActionAt = startMode == DispatchStartMode.MANUAL ? null
                : trip.getScheduledDepartureAt().minusSeconds(30 * 60);
    }

    public void ready(TripEntity currentTrip, Instant now) {
        readyDriverId = currentTrip.getDriver().getId();
        readyVehicleId = currentTrip.getVehicle().getId();
        readyAttemptNumber = currentTrip.getAttemptNumber();
        readyAssignmentRevision = assignmentRevision;
        readyAt = now;
        state = DispatchState.READY;
        attentionCode = null;
        nextActionAt = currentTrip.getScheduledDepartureAt().isAfter(now) ? currentTrip.getScheduledDepartureAt() : now;
        changed(now);
    }

    public boolean isReadyFor(TripEntity currentTrip) {
        return state == DispatchState.READY && readyAt != null && currentTrip.getDriver() != null
                && readyDriverId.equals(currentTrip.getDriver().getId())
                && readyVehicleId.equals(currentTrip.getVehicle().getId())
                && readyAttemptNumber == currentTrip.getAttemptNumber()
                && readyAssignmentRevision == assignmentRevision;
    }

    public void assignmentChanged(Instant now, boolean search) {
        clearReady();
        assignmentRevision++;
        boolean noDriver = trip.getDriver() == null;
        state = noDriver ? (search ? DispatchState.SEARCH_WAIT : DispatchState.ATTENTION) : DispatchState.WAITING_READY;
        attentionCode = noDriver && !search ? DispatchAttentionCode.NO_BACKUP : null;
        nextActionAt = state == DispatchState.SEARCH_WAIT ? trip.getScheduledDepartureAt().minusSeconds(30 * 60)
                : state == DispatchState.WAITING_READY ? trip.getScheduledDepartureAt() : null;
        changed(now);
    }

    public void driverUnavailable(Instant now) {
        clearReady();
        assignmentRevision++;
        if (backupEnabled && !backupDriverIds.isEmpty()) {
            state = DispatchState.SEARCH_WAIT;
            attentionCode = null;
            nextActionAt = trip.getScheduledDepartureAt().minusSeconds(30 * 60);
        } else {
            state = DispatchState.ATTENTION;
            attentionCode = DispatchAttentionCode.NO_BACKUP;
            nextActionAt = null;
        }
        changed(now);
    }

    public void searchNow(Instant now) {
        state = DispatchState.SEARCH_WAIT;
        nextActionAt = now;
        changed(now);
    }

    public void resumeForSchedule(long epoch, Instant now) {
        scheduleEpoch = epoch;
        clearReady();
        boolean noDriver = trip.getDriver() == null;
        state = noDriver ? (backupEnabled ? DispatchState.SEARCH_WAIT : DispatchState.ATTENTION) : DispatchState.WAITING_READY;
        attentionCode = state == DispatchState.ATTENTION ? DispatchAttentionCode.NO_BACKUP : null;
        nextActionAt = state == DispatchState.ATTENTION ? null : trip.getScheduledDepartureAt().minusSeconds(30 * 60);
        changed(now);
    }

    public void markOfferPending(Instant expiry, Instant now) {
        state = DispatchState.OFFER_PENDING;
        nextActionAt = dueAlertedAt == null && trip.getScheduledDepartureAt().isAfter(now)
                ? (expiry.isBefore(trip.getScheduledDepartureAt()) ? expiry : trip.getScheduledDepartureAt()) : expiry;
        attentionCode = null;
        changed(now);
    }

    public void attention(DispatchAttentionCode code, Instant now) {
        clearReady();
        state = DispatchState.ATTENTION;
        attentionCode = code;
        nextActionAt = code == DispatchAttentionCode.DRIVER_NOT_READY ? trip.getScheduledDepartureAt().plusSeconds(15 * 60) : null;
        changed(now);
    }

    public void dueAlerted(Instant now) { dueAlertedAt = now; changed(now); }
    public void waitUntilDeparture() { nextActionAt = trip.getScheduledDepartureAt(); }
    public void waitForOfferExpiry(Instant expiry) { nextActionAt = expiry; }

    public void started(Instant now) {
        clearReady();
        state = DispatchState.STARTED;
        attentionCode = null;
        nextActionAt = null;
        changed(now);
    }

    public void closed(Instant now) {
        clearReady();
        state = DispatchState.CLOSED;
        attentionCode = null;
        nextActionAt = null;
        changed(now);
    }

    public void pauseForSchedule(Instant now) { attention(DispatchAttentionCode.SCHEDULE_DISABLED, now); }

    public void setPolicy(DispatchStartMode mode, boolean backups, List<Long> candidateIds, long epoch, Instant now) {
        clearReady();
        startMode = mode;
        backupEnabled = backups;
        backupDriverIds.clear();
        backupDriverIds.addAll(candidateIds);
        scheduleEpoch = epoch;
        state = mode == DispatchStartMode.MANUAL ? DispatchState.MANUAL : DispatchState.WAITING_READY;
        attentionCode = null;
        nextActionAt = mode == DispatchStartMode.MANUAL ? null : trip.getScheduledDepartureAt().minusSeconds(30 * 60);
        changed(now);
    }

    private void clearReady() {
        readyDriverId = null;
        readyVehicleId = null;
        readyAttemptNumber = null;
        readyAssignmentRevision = null;
        readyAt = null;
    }
    private void changed(Instant now) { revision++; updatedAt = now; }
}
