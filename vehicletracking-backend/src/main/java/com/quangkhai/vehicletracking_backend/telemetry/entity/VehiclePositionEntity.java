package com.quangkhai.vehicletracking_backend.telemetry.entity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="vehicle_positions", schema="vehicle_tracking")
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class VehiclePositionEntity {
    @Id @Column(name="vehicle_id") private Long vehicleId;
    @OneToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="sample_id", nullable=false)
    private TelemetrySampleEntity sample;
    public VehiclePositionEntity(Long vehicleId, TelemetrySampleEntity sample) { this.vehicleId=vehicleId; this.sample=sample; }
    public void update(TelemetrySampleEntity sample) { this.sample=sample; }
}
