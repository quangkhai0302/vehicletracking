package com.quangkhai.vehicletracking_backend.simulation.entity;

public enum SimulationIncidentType {
    VEHICLE_BREAKDOWN("Xe gặp sự cố"),
    EMERGENCY_STOP("Dừng khẩn cấp"),
    ROAD_BLOCKED("Đường bị chặn"),
    OTHER("Sự cố khác");

    private final String label;

    SimulationIncidentType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
