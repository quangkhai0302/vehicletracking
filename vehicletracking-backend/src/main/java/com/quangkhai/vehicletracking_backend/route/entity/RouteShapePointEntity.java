package com.quangkhai.vehicletracking_backend.route.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name="route_shape_points",schema="vehicle_tracking")
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class RouteShapePointEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="route_id",nullable=false) private RouteEntity route;
    @Column(name="point_order",nullable=false) private int pointOrder;
    @Column(name="destination_stop_sequence",nullable=false) private int destinationStopSequence;
    @Column(nullable=false,precision=8,scale=6) private BigDecimal latitude;
    @Column(nullable=false,precision=9,scale=6) private BigDecimal longitude;
    public RouteShapePointEntity(int order,int destination,BigDecimal latitude,BigDecimal longitude) {
        this.pointOrder=order;this.destinationStopSequence=destination;this.latitude=latitude;this.longitude=longitude;
    }
    void setRoute(RouteEntity route) { this.route=route; }
}
