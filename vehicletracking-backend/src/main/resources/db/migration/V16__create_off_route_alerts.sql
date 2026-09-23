CREATE TABLE vehicle_tracking.trip_off_route_alert_states (
    trip_id BIGINT PRIMARY KEY REFERENCES vehicle_tracking.trips(id) ON DELETE RESTRICT,
    attempt_number INTEGER NOT NULL,
    breach_started_at TIMESTAMPTZ,
    consecutive_breach_count INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    episode INTEGER NOT NULL DEFAULT 0,
    last_distance_meters DOUBLE PRECISION,
    last_recorded_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_off_route_breach_count CHECK (consecutive_breach_count >= 0),
    CONSTRAINT chk_off_route_episode CHECK (episode >= 0),
    CONSTRAINT chk_off_route_distance CHECK (last_distance_meters IS NULL OR last_distance_meters >= 0)
);

ALTER TABLE vehicle_tracking.trip_notifications
    DROP CONSTRAINT chk_trip_notification_type;

ALTER TABLE vehicle_tracking.trip_notifications
    ADD CONSTRAINT chk_trip_notification_type
    CHECK (type IN ('REROUTE_CREATED', 'REROUTE_UNAVAILABLE', 'OFF_ROUTE_DETECTED'));

ALTER TABLE vehicle_tracking.trip_notifications
    ADD COLUMN measured_distance_meters DOUBLE PRECISION,
    ADD COLUMN threshold_distance_meters DOUBLE PRECISION,
    ADD COLUMN breach_duration_seconds BIGINT;

ALTER TABLE vehicle_tracking.trip_notifications
    ADD CONSTRAINT chk_trip_notification_off_route_metrics CHECK (
        (measured_distance_meters IS NULL OR measured_distance_meters >= 0)
        AND (threshold_distance_meters IS NULL OR threshold_distance_meters > 0)
        AND (breach_duration_seconds IS NULL OR breach_duration_seconds >= 0)
    );

COMMENT ON TABLE vehicle_tracking.trip_off_route_alert_states IS
    'Durable per-trip detector state used to debounce and re-arm off-route alerts.';
