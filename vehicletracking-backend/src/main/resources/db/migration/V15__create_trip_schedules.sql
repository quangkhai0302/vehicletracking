CREATE TABLE vehicle_tracking.trip_schedules (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(150),
    route_id BIGINT NOT NULL REFERENCES vehicle_tracking.routes(id) ON DELETE RESTRICT,
    vehicle_id BIGINT NOT NULL REFERENCES vehicle_tracking.vehicles(id) ON DELETE RESTRICT,
    driver_id BIGINT NOT NULL REFERENCES vehicle_tracking.drivers(id) ON DELETE RESTRICT,
    frequency VARCHAR(12) NOT NULL,
    scheduled_date DATE,
    weekdays_mask SMALLINT NOT NULL DEFAULT 0,
    departure_time TIME WITHOUT TIME ZONE NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    effective_from DATE NOT NULL,
    effective_until DATE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_run_at TIMESTAMPTZ,
    last_run_status VARCHAR(20),
    last_run_message VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_trip_schedules_frequency CHECK (frequency IN ('ONCE', 'WEEKLY')),
    CONSTRAINT chk_trip_schedules_recurrence CHECK (
        (frequency = 'ONCE' AND scheduled_date IS NOT NULL AND weekdays_mask = 0)
        OR (frequency = 'WEEKLY' AND scheduled_date IS NULL AND weekdays_mask BETWEEN 1 AND 127)
    ),
    CONSTRAINT chk_trip_schedules_effective_range CHECK (effective_until IS NULL OR effective_until >= effective_from),
    CONSTRAINT chk_trip_schedules_last_run_status CHECK (last_run_status IS NULL OR last_run_status IN ('SUCCESS', 'FAILED'))
);

CREATE INDEX idx_trip_schedules_enabled ON vehicle_tracking.trip_schedules(enabled, effective_from, effective_until);
CREATE INDEX idx_trip_schedules_route ON vehicle_tracking.trip_schedules(route_id);

ALTER TABLE vehicle_tracking.trips
    ADD COLUMN schedule_id BIGINT REFERENCES vehicle_tracking.trip_schedules(id) ON DELETE RESTRICT,
    ADD COLUMN schedule_occurrence_at TIMESTAMPTZ;

ALTER TABLE vehicle_tracking.trips
    ADD CONSTRAINT chk_trips_schedule_provenance_pair
    CHECK ((schedule_id IS NULL) = (schedule_occurrence_at IS NULL));

CREATE UNIQUE INDEX uq_trips_schedule_occurrence
    ON vehicle_tracking.trips(schedule_id, schedule_occurrence_at)
    WHERE schedule_id IS NOT NULL;

COMMENT ON TABLE vehicle_tracking.trip_schedules IS
    'Reusable local-time schedule which creates immutable trip snapshots.';
COMMENT ON COLUMN vehicle_tracking.trips.schedule_occurrence_at IS
    'Resolved UTC occurrence for the schedule; used only for idempotent automatic trip creation.';
