package com.quangkhai.vehicletracking_backend.route.dto;

/** Offset is the coordinate index within this section's flexible polyline. */
public record RouteInstruction(String action, String direction, String instruction, int offset) {}
