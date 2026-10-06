CREATE TABLE vehicle_tracking.simulation_incidents (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES vehicle_tracking.trips(id) ON DELETE RESTRICT,
    attempt_number INTEGER NOT NULL CHECK (attempt_number > 0),
    type VARCHAR(32) NOT NULL CHECK (type IN ('VEHICLE_BREAKDOWN', 'EMERGENCY_STOP', 'ROAD_BLOCKED', 'OTHER')),
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('MAJOR', 'CRITICAL')),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED')),
    detail VARCHAR(500),
    latitude DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    simulated_elapsed_seconds DOUBLE PRECISION NOT NULL CHECK (simulated_elapsed_seconds >= 0),
    idempotency_key UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    acknowledged_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    CONSTRAINT chk_simulation_incident_state_timestamps CHECK (
        (status = 'OPEN' AND acknowledged_at IS NULL AND resolved_at IS NULL)
        OR (status = 'ACKNOWLEDGED' AND acknowledged_at IS NOT NULL AND resolved_at IS NULL)
        OR (status = 'RESOLVED' AND resolved_at IS NOT NULL)
    )
);

CREATE INDEX idx_simulation_incidents_trip_attempt_created
    ON vehicle_tracking.simulation_incidents (trip_id, attempt_number, created_at DESC);
CREATE INDEX idx_simulation_incidents_status_created
    ON vehicle_tracking.simulation_incidents (status, created_at DESC);
CREATE UNIQUE INDEX uq_simulation_incidents_active_attempt
    ON vehicle_tracking.simulation_incidents (trip_id, attempt_number)
    WHERE status IN ('OPEN', 'ACKNOWLEDGED');

ALTER TABLE vehicle_tracking.trip_notifications
    ADD COLUMN simulation_incident_id BIGINT UNIQUE
        REFERENCES vehicle_tracking.simulation_incidents(id) ON DELETE RESTRICT;

ALTER TABLE vehicle_tracking.trip_notifications DROP CONSTRAINT chk_trip_notification_type;
ALTER TABLE vehicle_tracking.trip_notifications ADD CONSTRAINT chk_trip_notification_type
    CHECK (type IN (
        'REROUTE_CREATED', 'REROUTE_UNAVAILABLE', 'OFF_ROUTE_DETECTED', 'DRIVER_ROUTE_CHANGED',
        'DISPATCH_ATTENTION', 'DRIVER_UNAVAILABLE', 'DISPATCH_REASSIGNED', 'TRIP_AUTO_STARTED',
        'DIRECT_ASSIGNMENT_DECLINED', 'SIMULATION_INCIDENT'
    ));
