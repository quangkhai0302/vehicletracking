ALTER TABLE vehicle_tracking.telemetry_samples
    ADD CONSTRAINT uq_telemetry_sample_trip UNIQUE (id, trip_id),
    ADD CONSTRAINT uq_telemetry_sample_trip_source UNIQUE (id, trip_id, source);

CREATE TABLE vehicle_tracking.trip_checkin_states (
    trip_id BIGINT PRIMARY KEY REFERENCES vehicle_tracking.trips(id) ON DELETE RESTRICT,
    next_stop_sequence INTEGER,
    awaiting_exit BOOLEAN NOT NULL DEFAULT FALSE,
    last_sample_id BIGINT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    CONSTRAINT fk_checkin_state_stop FOREIGN KEY (trip_id, next_stop_sequence)
        REFERENCES vehicle_tracking.trip_stops(trip_id, sequence_number) ON DELETE RESTRICT,
    CONSTRAINT fk_checkin_state_sample FOREIGN KEY (last_sample_id)
        REFERENCES vehicle_tracking.telemetry_samples(id) ON DELETE RESTRICT,
    CONSTRAINT fk_checkin_state_sample_trip FOREIGN KEY (last_sample_id, trip_id)
        REFERENCES vehicle_tracking.telemetry_samples(id, trip_id) ON DELETE RESTRICT
);

CREATE TABLE vehicle_tracking.trip_stop_visits (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES vehicle_tracking.trips(id) ON DELETE RESTRICT,
    stop_sequence INTEGER NOT NULL,
    source VARCHAR(20) NOT NULL,
    evidence_kind VARCHAR(20) NOT NULL,
    actual_arrival_at TIMESTAMPTZ NOT NULL,
    simulated_arrival_at TIMESTAMPTZ,
    detected_at TIMESTAMPTZ NOT NULL,
    from_sample_id BIGINT REFERENCES vehicle_tracking.telemetry_samples(id) ON DELETE RESTRICT,
    to_sample_id BIGINT NOT NULL REFERENCES vehicle_tracking.telemetry_samples(id) ON DELETE RESTRICT,
    evidence_fraction DOUBLE PRECISION NOT NULL CHECK (evidence_fraction >= 0 AND evidence_fraction <= 1
        AND evidence_fraction <> 'NaN'::float8 AND evidence_fraction <> 'Infinity'::float8
        AND evidence_fraction <> '-Infinity'::float8),
    latitude DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90
        AND latitude <> 'NaN'::float8 AND latitude <> 'Infinity'::float8 AND latitude <> '-Infinity'::float8),
    longitude DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180
        AND longitude <> 'NaN'::float8 AND longitude <> 'Infinity'::float8 AND longitude <> '-Infinity'::float8),
    CONSTRAINT uq_trip_stop_visit UNIQUE (trip_id, stop_sequence),
    CONSTRAINT fk_visit_stop FOREIGN KEY (trip_id, stop_sequence)
        REFERENCES vehicle_tracking.trip_stops(trip_id, sequence_number) ON DELETE RESTRICT,
    CONSTRAINT fk_visit_from_sample_provenance FOREIGN KEY (from_sample_id, trip_id, source)
        REFERENCES vehicle_tracking.telemetry_samples(id, trip_id, source) ON DELETE RESTRICT,
    CONSTRAINT fk_visit_to_sample_provenance FOREIGN KEY (to_sample_id, trip_id, source)
        REFERENCES vehicle_tracking.telemetry_samples(id, trip_id, source) ON DELETE RESTRICT,
    CONSTRAINT chk_visit_source CHECK (source IN ('GPS', 'SIMULATOR')),
    CONSTRAINT chk_visit_evidence CHECK (evidence_kind IN ('POINT', 'SEGMENT', 'ROUTE_TRACE')),
    CONSTRAINT chk_visit_simulated_time CHECK ((source = 'SIMULATOR' AND simulated_arrival_at IS NOT NULL) OR
                                                (source = 'GPS' AND simulated_arrival_at IS NULL)),
    CONSTRAINT chk_visit_evidence_source CHECK ((source = 'GPS' AND evidence_kind IN ('POINT', 'SEGMENT')) OR
                                                 (source = 'SIMULATOR' AND evidence_kind IN ('POINT', 'ROUTE_TRACE')))
);
CREATE INDEX idx_trip_stop_visits_trip ON vehicle_tracking.trip_stop_visits(trip_id, stop_sequence);
COMMENT ON TABLE vehicle_tracking.trip_stop_visits IS 'Immutable automatic station pass evidence per trip stop occurrence';
COMMENT ON TABLE vehicle_tracking.trip_checkin_states IS 'Durable next-stop detector checkpoint; no check-in is backfilled';
