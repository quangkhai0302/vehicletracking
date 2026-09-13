CREATE TABLE vehicle_tracking.telemetry_samples (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    vehicle_id BIGINT NOT NULL REFERENCES vehicle_tracking.vehicles(id) ON DELETE RESTRICT,
    trip_id BIGINT NOT NULL REFERENCES vehicle_tracking.trips(id) ON DELETE RESTRICT,
    recorded_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    simulated_at TIMESTAMPTZ,
    latitude DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    speed_kmh DOUBLE PRECISION NOT NULL CHECK (speed_kmh BETWEEN 0 AND 500),
    heading DOUBLE PRECISION NOT NULL CHECK (heading >= 0 AND heading < 360),
    accuracy_meters DOUBLE PRECISION NOT NULL CHECK (accuracy_meters BETWEEN 0 AND 10000),
    source VARCHAR(20) NOT NULL,
    CONSTRAINT chk_telemetry_source CHECK (
        (source = 'GPS' AND simulated_at IS NULL) OR (source = 'SIMULATOR' AND simulated_at IS NOT NULL))
);
CREATE INDEX idx_telemetry_trip_time ON vehicle_tracking.telemetry_samples(trip_id, recorded_at DESC);
CREATE INDEX idx_telemetry_vehicle_time ON vehicle_tracking.telemetry_samples(vehicle_id, recorded_at DESC);
CREATE TABLE vehicle_tracking.vehicle_positions (
    vehicle_id BIGINT PRIMARY KEY REFERENCES vehicle_tracking.vehicles(id) ON DELETE RESTRICT,
    sample_id BIGINT NOT NULL UNIQUE REFERENCES vehicle_tracking.telemetry_samples(id) ON DELETE RESTRICT
);
CREATE TABLE vehicle_tracking.simulation_runs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trip_id BIGINT NOT NULL UNIQUE REFERENCES vehicle_tracking.trips(id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING','PAUSED','COMPLETED','STOPPED','FAILED')),
    multiplier INTEGER NOT NULL CHECK (multiplier IN (1,5,10)),
    elapsed_seconds DOUBLE PRECISION NOT NULL CHECK (elapsed_seconds >= 0 AND elapsed_seconds < 'Infinity'::float8),
    last_tick_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    error_message VARCHAR(255),
    replacement_trip_id BIGINT REFERENCES vehicle_tracking.trips(id) ON DELETE RESTRICT
);
CREATE INDEX idx_simulation_status ON vehicle_tracking.simulation_runs(status);
COMMENT ON TABLE vehicle_tracking.telemetry_samples IS 'Append-only GPS/simulator samples. recorded_at is wall clock; simulated_at is accelerated trip time.';
COMMENT ON TABLE vehicle_tracking.simulation_runs IS 'Backend clock checkpoint; running simulations recover paused after restart. Reset retains history.';
