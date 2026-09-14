package com.quangkhai.vehicletracking_backend.checkin.entity;

import com.quangkhai.vehicletracking_backend.telemetry.entity.TelemetrySampleEntity;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "trip_checkin_states", schema = "vehicle_tracking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripCheckInStateEntity {
    @Id
    @Column(name = "trip_id")
    private Long tripId;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "trip_id", nullable = false)
    private TripEntity trip;
    @Column(name = "next_stop_sequence") private Integer nextStopSequence;
    @Column(name = "awaiting_exit", nullable = false) private boolean awaitingExit;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "last_sample_id", nullable = false) private TelemetrySampleEntity lastSample;
    @Column(nullable = false) private long revision;

    public TripCheckInStateEntity(TripEntity trip, Integer nextStopSequence, boolean awaitingExit,
                                  TelemetrySampleEntity lastSample, long revision) {
        this.trip = trip; this.tripId = trip.getId(); this.nextStopSequence = nextStopSequence;
        this.awaitingExit = awaitingExit; this.lastSample = lastSample; this.revision = revision;
    }
    public void update(TelemetrySampleEntity sample, Integer next, boolean exit, long nextRevision) {
        this.lastSample = sample; this.nextStopSequence = next; this.awaitingExit = exit; this.revision = nextRevision;
    }
}
