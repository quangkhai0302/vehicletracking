-- Feature 009: durable reroute revisions, traffic evaluation checkpoints and in-app notifications.

CREATE TABLE vehicle_tracking.trip_route_revisions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES vehicle_tracking.trips(id) ON DELETE RESTRICT,
    source_route_id BIGINT NOT NULL REFERENCES vehicle_tracking.routes(id) ON DELETE RESTRICT,
    revision_number INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    reason_code VARCHAR(40) NOT NULL,
    reason_detail VARCHAR(255),
    trigger_incident_id VARCHAR(150),
    severity VARCHAR(20) NOT NULL,
    baseline_remaining_seconds BIGINT NOT NULL,
    revised_remaining_seconds BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    activated_at TIMESTAMPTZ NOT NULL,
    superseded_at TIMESTAMPTZ,
    CONSTRAINT uq_trip_route_revision_number UNIQUE (trip_id, revision_number),
    CONSTRAINT chk_trip_route_revision_status CHECK (status IN ('ACTIVE', 'SUPERSEDED')),
    CONSTRAINT chk_trip_route_revision_reason CHECK (reason_code IN ('ROAD_CLOSURE', 'TRAFFIC_DELAY')),
    CONSTRAINT chk_trip_route_revision_severity CHECK (severity IN ('CRITICAL', 'MAJOR')),
    CONSTRAINT chk_trip_route_revision_baseline CHECK (baseline_remaining_seconds >= 0),
    CONSTRAINT chk_trip_route_revision_revised CHECK (revised_remaining_seconds >= 0)
);
CREATE INDEX idx_trip_route_revisions_trip_created
    ON vehicle_tracking.trip_route_revisions (trip_id, created_at DESC, id DESC);
CREATE UNIQUE INDEX uq_trip_route_revision_active
    ON vehicle_tracking.trip_route_revisions (trip_id) WHERE status = 'ACTIVE';

CREATE TABLE vehicle_tracking.trip_route_revision_stops (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    revision_id BIGINT NOT NULL REFERENCES vehicle_tracking.trip_route_revisions(id) ON DELETE CASCADE,
    original_stop_sequence INTEGER NOT NULL,
    sequence_number INTEGER NOT NULL,
    station_id BIGINT NOT NULL REFERENCES vehicle_tracking.stations(id) ON DELETE RESTRICT,
    station_name VARCHAR(150) NOT NULL,
    latitude NUMERIC(8,6) NOT NULL,
    longitude NUMERIC(9,6) NOT NULL,
    dwell_duration_seconds INTEGER NOT NULL,
    baseline_arrival_at TIMESTAMPTZ NOT NULL,
    baseline_departure_at TIMESTAMPTZ NOT NULL,
    revised_arrival_at TIMESTAMPTZ NOT NULL,
    revised_departure_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_revision_stop_original UNIQUE (revision_id, original_stop_sequence),
    CONSTRAINT uq_revision_stop_sequence UNIQUE (revision_id, sequence_number),
    CONSTRAINT chk_revision_stop_sequence CHECK (sequence_number >= 1 AND original_stop_sequence >= 1),
    CONSTRAINT chk_revision_stop_dwell CHECK (dwell_duration_seconds BETWEEN 0 AND 3600),
    CONSTRAINT chk_revision_stop_schedule CHECK (
        baseline_departure_at = baseline_arrival_at + dwell_duration_seconds * INTERVAL '1 second'
        AND revised_departure_at = revised_arrival_at + dwell_duration_seconds * INTERVAL '1 second'
    ),
    CONSTRAINT chk_revision_stop_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_revision_stop_longitude CHECK (longitude BETWEEN -180 AND 180)
);

CREATE TABLE vehicle_tracking.trip_route_revision_sections (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    revision_id BIGINT NOT NULL REFERENCES vehicle_tracking.trip_route_revisions(id) ON DELETE CASCADE,
    section_sequence INTEGER NOT NULL,
    destination_stop_sequence INTEGER NOT NULL,
    encoded_polyline TEXT NOT NULL,
    distance_meters BIGINT NOT NULL,
    travel_duration_seconds BIGINT NOT NULL,
    base_travel_duration_seconds BIGINT NOT NULL,
    CONSTRAINT uq_revision_section_sequence UNIQUE (revision_id, section_sequence),
    CONSTRAINT fk_revision_section_destination FOREIGN KEY (revision_id, destination_stop_sequence)
        REFERENCES vehicle_tracking.trip_route_revision_stops (revision_id, original_stop_sequence) ON DELETE CASCADE
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT chk_revision_section_sequence CHECK (section_sequence >= 1 AND destination_stop_sequence >= 1),
    CONSTRAINT chk_revision_section_geometry CHECK (LENGTH(TRIM(encoded_polyline)) > 0),
    CONSTRAINT chk_revision_section_distance CHECK (distance_meters >= 0),
    CONSTRAINT chk_revision_section_duration CHECK (travel_duration_seconds >= 0 AND base_travel_duration_seconds >= 0)
);

CREATE TABLE vehicle_tracking.trip_traffic_alert_states (
    trip_id BIGINT PRIMARY KEY REFERENCES vehicle_tracking.trips(id) ON DELETE RESTRICT,
    last_traffic_fetched_at TIMESTAMPTZ,
    breach_fingerprint VARCHAR(255),
    breach_count INTEGER NOT NULL DEFAULT 0,
    last_triggered_fingerprint VARCHAR(255),
    last_trigger_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_traffic_alert_breach_count CHECK (breach_count >= 0)
);

CREATE TABLE vehicle_tracking.trip_notifications (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES vehicle_tracking.trips(id) ON DELETE RESTRICT,
    revision_id BIGINT REFERENCES vehicle_tracking.trip_route_revisions(id) ON DELETE SET NULL,
    type VARCHAR(40) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    title VARCHAR(180) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    incident_id VARCHAR(150),
    affected_stop_sequences VARCHAR(255) NOT NULL,
    baseline_eta_seconds BIGINT,
    revised_eta_seconds BIGINT,
    dedupe_key VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    read_at TIMESTAMPTZ,
    CONSTRAINT uq_trip_notification_dedupe UNIQUE (dedupe_key),
    CONSTRAINT chk_trip_notification_type CHECK (type IN ('REROUTE_CREATED', 'REROUTE_UNAVAILABLE')),
    CONSTRAINT chk_trip_notification_severity CHECK (severity IN ('CRITICAL', 'MAJOR')),
    CONSTRAINT chk_trip_notification_eta CHECK (
        (baseline_eta_seconds IS NULL OR baseline_eta_seconds >= 0)
        AND (revised_eta_seconds IS NULL OR revised_eta_seconds >= 0)
    )
);
CREATE INDEX idx_trip_notifications_created ON vehicle_tracking.trip_notifications (created_at DESC, id DESC);
CREATE INDEX idx_trip_notifications_trip ON vehicle_tracking.trip_notifications (trip_id, created_at DESC, id DESC);
CREATE INDEX idx_trip_notifications_unread ON vehicle_tracking.trip_notifications (read_at, created_at DESC, id DESC);

COMMENT ON TABLE vehicle_tracking.trip_route_revisions IS
    'Immutable alternative route/schedule revisions generated by Feature 009; original route snapshot remains unchanged';
COMMENT ON TABLE vehicle_tracking.trip_route_revision_stops IS
    'Baseline and revised schedule for each remaining original trip stop';
COMMENT ON TABLE vehicle_tracking.trip_route_revision_sections IS
    'Geometry and travel metrics returned for a route revision';
COMMENT ON TABLE vehicle_tracking.trip_traffic_alert_states IS
    'Durable consecutive-traffic-breach and cooldown checkpoint used for idempotent evaluation';
COMMENT ON TABLE vehicle_tracking.trip_notifications IS
    'In-app operational notifications emitted after a committed route revision or unavailable reroute';
