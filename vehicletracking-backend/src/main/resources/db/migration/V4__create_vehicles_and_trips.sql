CREATE TABLE vehicle_tracking.vehicles (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plate_number VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_vehicles_plate_number UNIQUE (plate_number),
    CONSTRAINT chk_vehicles_plate CHECK (plate_number ~ '^[A-Z0-9]{1,20}$'),
    CONSTRAINT chk_vehicles_name CHECK (LENGTH(TRIM(name)) > 0)
);

CREATE TABLE vehicle_tracking.trips (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    vehicle_id BIGINT NOT NULL REFERENCES vehicle_tracking.vehicles(id) ON DELETE RESTRICT,
    route_id BIGINT NOT NULL REFERENCES vehicle_tracking.routes(id) ON DELETE RESTRICT,
    vehicle_plate_snapshot VARCHAR(20) NOT NULL,
    scheduled_departure_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    started_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_trips_schedule CHECK (scheduled_departure_at >= TIMESTAMPTZ '2000-01-01 00:00:00+00' AND scheduled_departure_at < TIMESTAMPTZ '2101-01-01 00:00:00+00'),
    CONSTRAINT chk_trips_state CHECK (
        (status = 'SCHEDULED' AND started_at IS NULL AND ended_at IS NULL) OR
        (status = 'IN_PROGRESS' AND started_at IS NOT NULL AND ended_at IS NULL) OR
        (status = 'COMPLETED' AND started_at IS NOT NULL AND ended_at IS NOT NULL) OR
        (status = 'CANCELLED' AND ended_at IS NOT NULL)
    ),
    CONSTRAINT chk_trips_time_order CHECK (ended_at IS NULL OR started_at IS NULL OR ended_at >= started_at)
);
-- Backstop for concurrent starts, including writes outside the service lock.
CREATE UNIQUE INDEX uq_trips_running_vehicle ON vehicle_tracking.trips(vehicle_id) WHERE status = 'IN_PROGRESS';
CREATE INDEX idx_trips_vehicle_schedule ON vehicle_tracking.trips(vehicle_id, scheduled_departure_at DESC, id DESC);
CREATE INDEX idx_trips_schedule ON vehicle_tracking.trips(scheduled_departure_at DESC, id DESC);

CREATE TABLE vehicle_tracking.trip_stops (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES vehicle_tracking.trips(id) ON DELETE RESTRICT,
    station_id BIGINT NOT NULL REFERENCES vehicle_tracking.stations(id) ON DELETE RESTRICT,
    sequence_number INTEGER NOT NULL CHECK (sequence_number >= 1),
    station_name VARCHAR(150) NOT NULL CHECK (LENGTH(TRIM(station_name)) > 0),
    latitude NUMERIC(8,6) NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude NUMERIC(9,6) NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    checkin_radius_meters INTEGER NOT NULL CHECK (checkin_radius_meters BETWEEN 10 AND 1000),
    dwell_duration_seconds INTEGER NOT NULL CHECK (dwell_duration_seconds BETWEEN 0 AND 3600),
    arrival_offset_seconds BIGINT NOT NULL CHECK (arrival_offset_seconds >= 0),
    departure_offset_seconds BIGINT NOT NULL,
    planned_arrival_at TIMESTAMPTZ NOT NULL,
    planned_departure_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_trip_stops_sequence UNIQUE(trip_id, sequence_number),
    CONSTRAINT chk_trip_stops_offsets CHECK (departure_offset_seconds = arrival_offset_seconds + dwell_duration_seconds),
    CONSTRAINT chk_trip_stops_times CHECK (planned_departure_at = planned_arrival_at + dwell_duration_seconds * INTERVAL '1 second')
);
COMMENT ON TABLE vehicle_tracking.trips IS 'A vehicle assignment to an immutable route snapshot; planned times remain fixed after start';
COMMENT ON TABLE vehicle_tracking.trip_stops IS 'Trip-specific stop occurrences with immutable schedule and geofence radius; no GPS/check-in events yet';
