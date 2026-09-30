ALTER TABLE vehicle_tracking.trip_schedules
    ADD COLUMN start_mode VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN backup_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN dispatch_epoch BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_trip_schedules_start_mode CHECK (start_mode IN ('MANUAL', 'AUTO_IF_READY')),
    ADD CONSTRAINT chk_trip_schedules_backup_mode CHECK (start_mode = 'AUTO_IF_READY' OR backup_enabled = FALSE),
    ADD CONSTRAINT chk_trip_schedules_dispatch_epoch CHECK (dispatch_epoch >= 0);

CREATE TABLE vehicle_tracking.schedule_backup_drivers (
    schedule_id BIGINT NOT NULL REFERENCES vehicle_tracking.trip_schedules(id) ON DELETE RESTRICT,
    driver_id BIGINT NOT NULL REFERENCES vehicle_tracking.drivers(id) ON DELETE RESTRICT,
    priority SMALLINT NOT NULL CHECK (priority BETWEEN 1 AND 20),
    PRIMARY KEY (schedule_id, priority),
    CONSTRAINT uq_schedule_backup_driver UNIQUE (schedule_id, driver_id)
);

CREATE TABLE vehicle_tracking.trip_dispatches (
    trip_id BIGINT PRIMARY KEY REFERENCES vehicle_tracking.trips(id) ON DELETE RESTRICT,
    start_mode VARCHAR(20) NOT NULL CHECK (start_mode IN ('MANUAL', 'AUTO_IF_READY')),
    state VARCHAR(24) NOT NULL CHECK (state IN ('MANUAL', 'WAITING_READY', 'READY', 'SEARCH_WAIT', 'OFFER_PENDING', 'ATTENTION', 'STARTED', 'CLOSED')),
    backup_enabled BOOLEAN NOT NULL,
    primary_driver_id BIGINT NOT NULL REFERENCES vehicle_tracking.drivers(id) ON DELETE RESTRICT,
    schedule_epoch BIGINT NOT NULL CHECK (schedule_epoch >= 0),
    baseline_duration_seconds BIGINT NOT NULL CHECK (baseline_duration_seconds > 0),
    assignment_revision BIGINT NOT NULL DEFAULT 0 CHECK (assignment_revision >= 0),
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    ready_driver_id BIGINT REFERENCES vehicle_tracking.drivers(id) ON DELETE RESTRICT,
    ready_vehicle_id BIGINT REFERENCES vehicle_tracking.vehicles(id) ON DELETE RESTRICT,
    ready_attempt_number INTEGER CHECK (ready_attempt_number >= 1),
    ready_assignment_revision BIGINT,
    ready_at TIMESTAMPTZ,
    attention_code VARCHAR(32) CHECK (attention_code IN ('DRIVER_NOT_READY', 'NO_BACKUP', 'RESOURCE_UNAVAILABLE', 'START_FAILED', 'SCHEDULE_DISABLED', 'WINDOW_EXPIRED')),
    due_alerted_at TIMESTAMPTZ,
    next_action_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_trip_dispatch_ready_fields CHECK (
        (ready_driver_id IS NULL AND ready_vehicle_id IS NULL AND ready_attempt_number IS NULL
         AND ready_assignment_revision IS NULL AND ready_at IS NULL)
        OR (ready_driver_id IS NOT NULL AND ready_vehicle_id IS NOT NULL AND ready_attempt_number IS NOT NULL
            AND ready_assignment_revision IS NOT NULL AND ready_at IS NOT NULL)
    ),
    CONSTRAINT chk_trip_dispatch_ready_state CHECK ((state = 'READY') = (ready_at IS NOT NULL)),
    CONSTRAINT chk_trip_dispatch_manual_mode CHECK (start_mode = 'AUTO_IF_READY' OR (state IN ('MANUAL', 'CLOSED') AND backup_enabled = FALSE))
);
CREATE INDEX idx_trip_dispatch_next_action ON vehicle_tracking.trip_dispatches(next_action_at, trip_id)
    WHERE next_action_at IS NOT NULL;

CREATE TABLE vehicle_tracking.trip_dispatch_candidates (
    trip_id BIGINT NOT NULL REFERENCES vehicle_tracking.trip_dispatches(trip_id) ON DELETE RESTRICT,
    driver_id BIGINT NOT NULL REFERENCES vehicle_tracking.drivers(id) ON DELETE RESTRICT,
    priority SMALLINT NOT NULL CHECK (priority BETWEEN 1 AND 20),
    PRIMARY KEY (trip_id, priority),
    CONSTRAINT uq_trip_dispatch_candidate UNIQUE (trip_id, driver_id)
);

CREATE TABLE vehicle_tracking.trip_dispatch_offers (
    id UUID PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES vehicle_tracking.trip_dispatches(trip_id) ON DELETE RESTRICT,
    candidate_driver_id BIGINT NOT NULL REFERENCES vehicle_tracking.drivers(id) ON DELETE RESTRICT,
    priority SMALLINT NOT NULL CHECK (priority BETWEEN 1 AND 20),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'CANCELLED')),
    dispatch_revision BIGINT NOT NULL CHECK (dispatch_revision >= 0),
    offered_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    responded_at TIMESTAMPTZ,
    response_reason VARCHAR(500),
    CONSTRAINT chk_trip_dispatch_offer_window CHECK (expires_at > offered_at)
);
CREATE UNIQUE INDEX uq_trip_dispatch_pending_offer ON vehicle_tracking.trip_dispatch_offers(trip_id)
    WHERE status = 'PENDING';
CREATE UNIQUE INDEX uq_driver_pending_dispatch_offer ON vehicle_tracking.trip_dispatch_offers(candidate_driver_id)
    WHERE status = 'PENDING';
CREATE INDEX idx_trip_dispatch_offer_expiry ON vehicle_tracking.trip_dispatch_offers(status, expires_at);
CREATE INDEX idx_driver_dispatch_offers ON vehicle_tracking.trip_dispatch_offers(candidate_driver_id, status);

CREATE TABLE vehicle_tracking.driver_unavailability (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_trip_id BIGINT NOT NULL REFERENCES vehicle_tracking.trips(id) ON DELETE RESTRICT,
    driver_id BIGINT NOT NULL REFERENCES vehicle_tracking.drivers(id) ON DELETE RESTRICT,
    assignment_revision BIGINT NOT NULL CHECK (assignment_revision >= 0),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_driver_unavailability_assignment UNIQUE (source_trip_id, driver_id, assignment_revision),
    CONSTRAINT chk_driver_unavailability_window CHECK (starts_at < ends_at),
    CONSTRAINT chk_driver_unavailability_reason CHECK (length(trim(reason)) BETWEEN 3 AND 500)
);
CREATE INDEX idx_driver_unavailability_window ON vehicle_tracking.driver_unavailability(driver_id, starts_at, ends_at);

CREATE TABLE vehicle_tracking.trip_dispatch_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES vehicle_tracking.trip_dispatches(trip_id) ON DELETE RESTRICT,
    revision BIGINT NOT NULL CHECK (revision >= 0),
    kind VARCHAR(32) NOT NULL CHECK (kind IN ('CREATED', 'POLICY_CHANGED', 'READY', 'BUSY', 'SEARCH_OPENED',
        'OFFERED', 'OFFER_DECLINED', 'OFFER_EXPIRED', 'OFFER_CANCELLED', 'REASSIGNED', 'DUE_ALERTED',
        'AUTO_STARTED', 'OVERRIDE_STARTED', 'ATTENTION_CHANGED', 'SCHEDULE_PAUSED', 'SCHEDULE_RESUMED', 'CLOSED')),
    actor_kind VARCHAR(8) NOT NULL CHECK (actor_kind IN ('SYSTEM', 'ADMIN', 'DRIVER')),
    actor_account_id BIGINT REFERENCES vehicle_tracking.user_accounts(id) ON DELETE RESTRICT,
    actor_driver_id BIGINT REFERENCES vehicle_tracking.drivers(id) ON DELETE RESTRICT,
    from_driver_id BIGINT REFERENCES vehicle_tracking.drivers(id) ON DELETE RESTRICT,
    to_driver_id BIGINT REFERENCES vehicle_tracking.drivers(id) ON DELETE RESTRICT,
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_trip_dispatch_event_revision UNIQUE (trip_id, revision),
    CONSTRAINT chk_trip_dispatch_event_actor CHECK (
        (actor_kind = 'SYSTEM' AND actor_account_id IS NULL AND actor_driver_id IS NULL)
        OR (actor_kind = 'ADMIN' AND actor_account_id IS NOT NULL AND actor_driver_id IS NULL)
        OR (actor_kind = 'DRIVER' AND actor_account_id IS NOT NULL AND actor_driver_id IS NOT NULL)
    )
);
CREATE INDEX idx_trip_dispatch_events_latest ON vehicle_tracking.trip_dispatch_events(trip_id, created_at DESC, id DESC);

CREATE TABLE vehicle_tracking.driver_dispatch_inbox (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recipient_driver_id BIGINT NOT NULL REFERENCES vehicle_tracking.drivers(id) ON DELETE RESTRICT,
    trip_id BIGINT NOT NULL REFERENCES vehicle_tracking.trips(id) ON DELETE RESTRICT,
    offer_id UUID REFERENCES vehicle_tracking.trip_dispatch_offers(id) ON DELETE RESTRICT,
    kind VARCHAR(32) NOT NULL CHECK (kind IN ('TRIP_ASSIGNED', 'TRIP_UNASSIGNED', 'READY_WINDOW_OPEN',
        'OFFER_RECEIVED', 'OFFER_CANCELLED', 'OFFER_EXPIRED', 'TRIP_STARTED')),
    title VARCHAR(180) NOT NULL,
    detail VARCHAR(500),
    dedupe_key VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    read_at TIMESTAMPTZ
);
CREATE INDEX idx_driver_dispatch_inbox_latest ON vehicle_tracking.driver_dispatch_inbox(recipient_driver_id, created_at DESC, id DESC);

ALTER TABLE vehicle_tracking.trip_notifications DROP CONSTRAINT chk_trip_notification_type;
ALTER TABLE vehicle_tracking.trip_notifications ADD CONSTRAINT chk_trip_notification_type
    CHECK (type IN ('REROUTE_CREATED', 'REROUTE_UNAVAILABLE', 'OFF_ROUTE_DETECTED', 'DRIVER_ROUTE_CHANGED',
        'DISPATCH_ATTENTION', 'DRIVER_UNAVAILABLE', 'DISPATCH_REASSIGNED', 'TRIP_AUTO_STARTED'));
