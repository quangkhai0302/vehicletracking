package com.quangkhai.vehicletracking_backend.reroute.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "trip_route_revision_stops", schema = "vehicle_tracking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripRouteRevisionStopEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "revision_id", nullable = false)
    private TripRouteRevisionEntity revision;

    @Column(name = "original_stop_sequence", nullable = false)
    private int originalStopSequence;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(name = "station_id", nullable = false)
    private long stationId;

    @Column(name = "station_name", nullable = false, length = 150)
    private String stationName;

    @Column(nullable = false, precision = 8, scale = 6)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "dwell_duration_seconds", nullable = false)
    private int dwellDurationSeconds;

    @Column(name = "baseline_arrival_at", nullable = false)
    private Instant baselineArrivalAt;

    @Column(name = "baseline_departure_at", nullable = false)
    private Instant baselineDepartureAt;

    @Column(name = "revised_arrival_at", nullable = false)
    private Instant revisedArrivalAt;

    @Column(name = "revised_departure_at", nullable = false)
    private Instant revisedDepartureAt;

    public TripRouteRevisionStopEntity(int originalStopSequence, int sequenceNumber, long stationId,
                                       String stationName, BigDecimal latitude, BigDecimal longitude,
                                       int dwellDurationSeconds, Instant baselineArrivalAt,
                                       Instant baselineDepartureAt, Instant revisedArrivalAt,
                                       Instant revisedDepartureAt) {
        this.originalStopSequence = originalStopSequence;
        this.sequenceNumber = sequenceNumber;
        this.stationId = stationId;
        this.stationName = stationName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.dwellDurationSeconds = dwellDurationSeconds;
        this.baselineArrivalAt = baselineArrivalAt;
        this.baselineDepartureAt = baselineDepartureAt;
        this.revisedArrivalAt = revisedArrivalAt;
        this.revisedDepartureAt = revisedDepartureAt;
    }

    void assignTo(TripRouteRevisionEntity revision) {
        this.revision = revision;
    }
}
