package com.quangkhai.vehicletracking_backend.trip.entity;

import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse.RouteStopResponse;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "trip_stops", schema = "vehicle_tracking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripStopEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false) private TripEntity trip;
    @Column(name = "station_id", nullable = false) private Long stationId;
    @Column(name = "sequence_number", nullable = false) private Integer sequenceNumber;
    @Column(name = "station_name", nullable = false, length = 150) private String stationName;
    @Column(nullable = false, precision = 8, scale = 6) private BigDecimal latitude;
    @Column(nullable = false, precision = 9, scale = 6) private BigDecimal longitude;
    @Column(name = "checkin_radius_meters", nullable = false) private Integer checkinRadiusMeters;
    @Column(name = "dwell_duration_seconds", nullable = false) private Integer dwellDurationSeconds;
    @Column(name = "arrival_offset_seconds", nullable = false) private Long arrivalOffsetSeconds;
    @Column(name = "departure_offset_seconds", nullable = false) private Long departureOffsetSeconds;
    @Column(name = "planned_arrival_at", nullable = false) private Instant plannedArrivalAt;
    @Column(name = "planned_departure_at", nullable = false) private Instant plannedDepartureAt;

    public TripStopEntity(RouteStopResponse stop, int radius, Instant departure) {
        stationId = stop.stationId(); sequenceNumber = stop.sequenceNumber();
        stationName = stop.stationName(); latitude = stop.latitude(); longitude = stop.longitude();
        checkinRadiusMeters = radius; dwellDurationSeconds = stop.dwellDurationSeconds();
        arrivalOffsetSeconds = stop.arrivalOffsetSeconds(); departureOffsetSeconds = stop.departureOffsetSeconds();
        plannedArrivalAt = departure.plusSeconds(arrivalOffsetSeconds);
        plannedDepartureAt = departure.plusSeconds(departureOffsetSeconds);
    }
    void assignTo(TripEntity trip) { this.trip = trip; }
}
