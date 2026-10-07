ALTER TABLE vehicle_tracking.trips
    ADD COLUMN seat_capacity_snapshot INTEGER;

ALTER TABLE vehicle_tracking.trips
    ADD CONSTRAINT ck_trips_seat_capacity_snapshot_positive
    CHECK (seat_capacity_snapshot IS NULL OR seat_capacity_snapshot > 0);

-- A historical trip's capacity at departure cannot be inferred from today's vehicle.
