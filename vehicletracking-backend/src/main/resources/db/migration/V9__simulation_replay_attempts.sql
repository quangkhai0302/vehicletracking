ALTER TABLE vehicle_tracking.trips ADD COLUMN attempt_number INTEGER NOT NULL DEFAULT 1 CHECK (attempt_number > 0);
ALTER TABLE vehicle_tracking.telemetry_samples ADD COLUMN attempt_number INTEGER NOT NULL DEFAULT 1 CHECK (attempt_number > 0);
ALTER TABLE vehicle_tracking.trip_stop_visits ADD COLUMN attempt_number INTEGER NOT NULL DEFAULT 1 CHECK (attempt_number > 0);
ALTER TABLE vehicle_tracking.trip_checkin_states ADD COLUMN attempt_number INTEGER NOT NULL DEFAULT 1 CHECK (attempt_number > 0);
ALTER TABLE vehicle_tracking.trip_stop_visits DROP CONSTRAINT uq_trip_stop_visit;
ALTER TABLE vehicle_tracking.trip_stop_visits ADD CONSTRAINT uq_trip_attempt_stop_visit UNIQUE (trip_id, attempt_number, stop_sequence);
ALTER TABLE vehicle_tracking.telemetry_samples ADD CONSTRAINT uq_sample_attempt UNIQUE (id, trip_id, attempt_number);
ALTER TABLE vehicle_tracking.trip_stop_visits ADD CONSTRAINT fk_visit_to_attempt FOREIGN KEY (to_sample_id, trip_id, attempt_number)
    REFERENCES vehicle_tracking.telemetry_samples(id, trip_id, attempt_number);
ALTER TABLE vehicle_tracking.trip_stop_visits ADD CONSTRAINT fk_visit_from_attempt FOREIGN KEY (from_sample_id, trip_id, attempt_number)
    REFERENCES vehicle_tracking.telemetry_samples(id, trip_id, attempt_number);
CREATE INDEX idx_samples_trip_attempt ON vehicle_tracking.telemetry_samples(trip_id, attempt_number, recorded_at DESC);
CREATE TABLE vehicle_tracking.simulation_attempts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES vehicle_tracking.trips(id),
    attempt_number INTEGER NOT NULL CHECK (attempt_number > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING','PAUSED','COMPLETED','STOPPED','FAILED')),
    trip_status VARCHAR(20) NOT NULL CHECK (trip_status IN ('SCHEDULED','IN_PROGRESS','COMPLETED','CANCELLED')),
    elapsed_seconds DOUBLE PRECISION NOT NULL CHECK (elapsed_seconds >= 0 AND elapsed_seconds < 'Infinity'::float8),
    multiplier INTEGER NOT NULL CHECK (multiplier IN (1,5,10)),
    scheduled_departure_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    archived_at TIMESTAMPTZ NOT NULL,
    error_message VARCHAR(255),
    UNIQUE (trip_id, attempt_number)
);
