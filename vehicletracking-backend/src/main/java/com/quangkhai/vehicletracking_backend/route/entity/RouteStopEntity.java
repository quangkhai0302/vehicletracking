package com.quangkhai.vehicletracking_backend.route.entity;

import com.quangkhai.vehicletracking_backend.station.entity.StationEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "route_stops", schema = "vehicle_tracking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RouteStopEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private RouteEntity route;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private StationEntity station;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @Column(name = "station_name_snapshot", nullable = false, length = 150)
    private String stationNameSnapshot;

    @Column(name = "latitude_snapshot", nullable = false, precision = 8, scale = 6)
    private BigDecimal latitudeSnapshot;

    @Column(name = "longitude_snapshot", nullable = false, precision = 9, scale = 6)
    private BigDecimal longitudeSnapshot;

    @Column(name = "dwell_duration_seconds", nullable = false)
    private Integer dwellDurationSeconds;

    public RouteStopEntity(
            StationEntity station,
            Integer sequenceNumber,
            String stationNameSnapshot,
            BigDecimal latitudeSnapshot,
            BigDecimal longitudeSnapshot,
            Integer dwellDurationSeconds
    ) {
        this.station = station;
        this.sequenceNumber = sequenceNumber;
        this.stationNameSnapshot = stationNameSnapshot;
        this.latitudeSnapshot = latitudeSnapshot;
        this.longitudeSnapshot = longitudeSnapshot;
        this.dwellDurationSeconds = dwellDurationSeconds;
    }

    /** Refreshes the route-definition snapshot after the referenced station changes. */
    public void refreshSnapshotFromStation() {
        this.stationNameSnapshot = station.getName();
        this.latitudeSnapshot = station.getLatitude();
        this.longitudeSnapshot = station.getLongitude();
    }

    void setRoute(RouteEntity route) {
        this.route = route;
    }
}
