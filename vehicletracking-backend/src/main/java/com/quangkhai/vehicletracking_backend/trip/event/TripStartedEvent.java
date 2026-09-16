package com.quangkhai.vehicletracking_backend.trip.event;

/** Published after a trip lifecycle transition to IN_PROGRESS. */
public record TripStartedEvent(long tripId) {}
