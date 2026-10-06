ALTER TABLE vehicle_tracking.vehicles
    ADD COLUMN seat_capacity INTEGER;

ALTER TABLE vehicle_tracking.vehicles
    ADD CONSTRAINT ck_vehicles_seat_capacity_positive
    CHECK (seat_capacity IS NULL OR seat_capacity > 0);

ALTER TABLE vehicle_tracking.trip_stops
    ADD COLUMN expected_employee_boarding_count INTEGER;

ALTER TABLE vehicle_tracking.trip_stops
    ADD CONSTRAINT ck_trip_stops_expected_employee_boarding_nonnegative
    CHECK (expected_employee_boarding_count IS NULL OR expected_employee_boarding_count >= 0);

ALTER TABLE vehicle_tracking.trip_stop_visits
    ADD COLUMN employee_boarding_count INTEGER;

ALTER TABLE vehicle_tracking.trip_stop_visits
    ADD CONSTRAINT ck_trip_stop_visits_employee_boarding_nonnegative
    CHECK (employee_boarding_count IS NULL OR employee_boarding_count >= 0);

CREATE TABLE vehicle_tracking.trip_schedule_stop_boardings (
    schedule_id BIGINT NOT NULL REFERENCES vehicle_tracking.trip_schedules(id) ON DELETE CASCADE,
    stop_sequence INTEGER NOT NULL CHECK (stop_sequence > 0),
    expected_employee_boarding_count INTEGER NOT NULL CHECK (expected_employee_boarding_count >= 0),
    CONSTRAINT pk_trip_schedule_stop_boardings PRIMARY KEY (schedule_id, stop_sequence)
);
