CREATE TABLE vehicle_tracking.trip_turnaround_plans (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES vehicle_tracking.trips(id) ON DELETE RESTRICT,
    attempt_number INTEGER NOT NULL CHECK (attempt_number >= 1),
    vehicle_id BIGINT NOT NULL REFERENCES vehicle_tracking.vehicles(id) ON DELETE RESTRICT,
    driver_id BIGINT REFERENCES vehicle_tracking.drivers(id) ON DELETE RESTRICT,
    execution_kind VARCHAR(16) NOT NULL DEFAULT 'REAL' CHECK (execution_kind IN ('REAL', 'SIMULATION')),
    lifecycle VARCHAR(16) NOT NULL DEFAULT 'RESERVED' CHECK (lifecycle IN ('RESERVED', 'STARTED', 'TERMINAL', 'RELEASED')),
    planned_departure_at TIMESTAMPTZ NOT NULL,
    baseline_duration_seconds BIGINT NOT NULL CHECK (baseline_duration_seconds > 0),
    depot_to_origin_seconds INTEGER NOT NULL CHECK (depot_to_origin_seconds BETWEEN 0 AND 86400),
    vehicle_terminal_to_depot_seconds INTEGER NOT NULL CHECK (vehicle_terminal_to_depot_seconds BETWEEN 0 AND 86400),
    driver_terminal_to_depot_seconds INTEGER CHECK (driver_terminal_to_depot_seconds BETWEEN 0 AND 86400),
    preparation_seconds INTEGER NOT NULL DEFAULT 900 CHECK (preparation_seconds BETWEEN 0 AND 7200),
    driver_planned_return_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    terminal_at TIMESTAMPTZ,
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_trip_turnaround_plan_attempt UNIQUE (trip_id, attempt_number),
    CONSTRAINT ck_turnaround_driver_fields CHECK (
        (driver_id IS NULL AND driver_terminal_to_depot_seconds IS NULL AND driver_planned_return_at IS NULL)
        OR (driver_id IS NOT NULL AND driver_terminal_to_depot_seconds IS NOT NULL AND driver_planned_return_at IS NOT NULL)
    ),
    CONSTRAINT ck_turnaround_lifecycle CHECK (
        (lifecycle = 'RESERVED' AND started_at IS NULL AND terminal_at IS NULL)
        OR (lifecycle = 'STARTED' AND started_at IS NOT NULL AND terminal_at IS NULL)
        OR (lifecycle = 'TERMINAL' AND started_at IS NOT NULL AND terminal_at IS NOT NULL AND terminal_at >= started_at)
        OR (lifecycle = 'RELEASED' AND started_at IS NULL AND terminal_at IS NULL)
    )
);
CREATE INDEX idx_turnaround_vehicle_departure
    ON vehicle_tracking.trip_turnaround_plans(vehicle_id, planned_departure_at);
CREATE INDEX idx_turnaround_driver_departure
    ON vehicle_tracking.trip_turnaround_plans(driver_id, planned_departure_at)
    WHERE driver_id IS NOT NULL;

CREATE TABLE vehicle_tracking.resource_depot_confirmations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    resource_kind VARCHAR(10) NOT NULL CHECK (resource_kind IN ('VEHICLE', 'DRIVER')),
    vehicle_id BIGINT REFERENCES vehicle_tracking.vehicles(id) ON DELETE RESTRICT,
    driver_id BIGINT REFERENCES vehicle_tracking.drivers(id) ON DELETE RESTRICT,
    trip_id BIGINT REFERENCES vehicle_tracking.trips(id) ON DELETE RESTRICT,
    attempt_number INTEGER CHECK (attempt_number IS NULL OR attempt_number >= 1),
    resource_label_snapshot VARCHAR(120) NOT NULL,
    execution_kind VARCHAR(16) NOT NULL CHECK (execution_kind IN ('REAL', 'SIMULATION')),
    source VARCHAR(24) NOT NULL CHECK (source IN ('BASELINE_MANUAL', 'TRIP_RETURN_MANUAL', 'SIMULATED')),
    ready_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ NOT NULL,
    confirmed_by_account_id BIGINT REFERENCES vehicle_tracking.user_accounts(id) ON DELETE RESTRICT,
    reason VARCHAR(500) NOT NULL CHECK (length(btrim(reason)) BETWEEN 3 AND 500),
    idempotency_key UUID NOT NULL UNIQUE,
    CONSTRAINT ck_confirmation_resource CHECK (
        (resource_kind = 'VEHICLE' AND vehicle_id IS NOT NULL AND driver_id IS NULL)
        OR (resource_kind = 'DRIVER' AND driver_id IS NOT NULL AND vehicle_id IS NULL)
    ),
    CONSTRAINT ck_confirmation_trip_pair CHECK ((trip_id IS NULL) = (attempt_number IS NULL)),
    CONSTRAINT ck_confirmation_source_kind CHECK (
        (source = 'SIMULATED' AND execution_kind = 'SIMULATION')
        OR (source <> 'SIMULATED' AND execution_kind = 'REAL')
    ),
    CONSTRAINT ck_confirmation_time CHECK (ready_at <= confirmed_at)
);
CREATE UNIQUE INDEX uq_confirmation_trip_resource
    ON vehicle_tracking.resource_depot_confirmations(trip_id, attempt_number, resource_kind)
    WHERE trip_id IS NOT NULL;
CREATE INDEX idx_confirmation_vehicle_ready
    ON vehicle_tracking.resource_depot_confirmations(vehicle_id, ready_at DESC)
    WHERE vehicle_id IS NOT NULL;
CREATE INDEX idx_confirmation_driver_ready
    ON vehicle_tracking.resource_depot_confirmations(driver_id, ready_at DESC)
    WHERE driver_id IS NOT NULL;

CREATE OR REPLACE FUNCTION vehicle_tracking.reject_resource_confirmation_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'resource depot confirmations are immutable';
END;
$$;
CREATE TRIGGER trg_resource_confirmation_immutable
    BEFORE UPDATE OR DELETE ON vehicle_tracking.resource_depot_confirmations
    FOR EACH ROW EXECUTE FUNCTION vehicle_tracking.reject_resource_confirmation_mutation();

ALTER TABLE vehicle_tracking.trip_schedules
    ADD COLUMN depot_to_origin_seconds INTEGER NOT NULL DEFAULT 0 CHECK (depot_to_origin_seconds BETWEEN 0 AND 86400),
    ADD COLUMN vehicle_terminal_to_depot_seconds INTEGER NOT NULL DEFAULT 0 CHECK (vehicle_terminal_to_depot_seconds BETWEEN 0 AND 86400),
    ADD COLUMN driver_terminal_to_depot_seconds INTEGER NOT NULL DEFAULT 0 CHECK (driver_terminal_to_depot_seconds BETWEEN 0 AND 86400),
    ADD COLUMN preparation_seconds INTEGER NOT NULL DEFAULT 900 CHECK (preparation_seconds BETWEEN 0 AND 7200),
    ADD COLUMN execution_kind VARCHAR(16) NOT NULL DEFAULT 'REAL' CHECK (execution_kind IN ('REAL', 'SIMULATION'));
