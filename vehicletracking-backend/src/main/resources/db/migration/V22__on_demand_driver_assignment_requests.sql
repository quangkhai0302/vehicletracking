CREATE TABLE vehicle_tracking.trip_assignment_requests (
    id UUID PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES vehicle_tracking.trips(id) ON DELETE RESTRICT,
    candidate_driver_id BIGINT NOT NULL REFERENCES vehicle_tracking.drivers(id) ON DELETE RESTRICT,
    requested_by_account_id BIGINT NOT NULL REFERENCES vehicle_tracking.user_accounts(id) ON DELETE RESTRICT,
    status VARCHAR(16) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    responded_at TIMESTAMPTZ,
    response_reason VARCHAR(500),
    CONSTRAINT chk_trip_assignment_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED')),
    CONSTRAINT chk_trip_assignment_response CHECK (
        (status = 'PENDING' AND responded_at IS NULL AND response_reason IS NULL)
        OR (status = 'ACCEPTED' AND responded_at IS NOT NULL AND response_reason IS NULL)
        OR (status IN ('DECLINED', 'CANCELLED') AND responded_at IS NOT NULL
            AND response_reason IS NOT NULL AND LENGTH(TRIM(response_reason)) BETWEEN 3 AND 500)
    )
);
CREATE UNIQUE INDEX uq_trip_assignment_pending_trip
    ON vehicle_tracking.trip_assignment_requests(trip_id) WHERE status = 'PENDING';
CREATE UNIQUE INDEX uq_trip_assignment_pending_driver
    ON vehicle_tracking.trip_assignment_requests(candidate_driver_id) WHERE status = 'PENDING';
CREATE INDEX idx_trip_assignment_driver_status
    ON vehicle_tracking.trip_assignment_requests(candidate_driver_id, status, requested_at DESC);
CREATE INDEX idx_trip_assignment_trip_requested
    ON vehicle_tracking.trip_assignment_requests(trip_id, requested_at DESC);

ALTER TABLE vehicle_tracking.driver_dispatch_inbox
    ADD COLUMN assignment_request_id UUID REFERENCES vehicle_tracking.trip_assignment_requests(id) ON DELETE RESTRICT;
ALTER TABLE vehicle_tracking.driver_dispatch_inbox
    DROP CONSTRAINT driver_dispatch_inbox_kind_check;
ALTER TABLE vehicle_tracking.driver_dispatch_inbox
    ADD CONSTRAINT driver_dispatch_inbox_kind_check CHECK (kind IN (
        'TRIP_ASSIGNED', 'TRIP_UNASSIGNED', 'READY_WINDOW_OPEN', 'OFFER_RECEIVED',
        'OFFER_CANCELLED', 'OFFER_EXPIRED', 'TRIP_STARTED', 'DIRECT_ASSIGNMENT_REQUESTED',
        'DIRECT_ASSIGNMENT_CANCELLED', 'DIRECT_ASSIGNMENT_ACCEPTED'));
ALTER TABLE vehicle_tracking.driver_dispatch_inbox
    ADD CONSTRAINT chk_driver_dispatch_inbox_source CHECK (NOT (offer_id IS NOT NULL AND assignment_request_id IS NOT NULL));
CREATE INDEX idx_driver_dispatch_inbox_assignment_request
    ON vehicle_tracking.driver_dispatch_inbox(assignment_request_id);

ALTER TABLE vehicle_tracking.trip_notifications DROP CONSTRAINT chk_trip_notification_type;
ALTER TABLE vehicle_tracking.trip_notifications ADD CONSTRAINT chk_trip_notification_type
    CHECK (type IN ('REROUTE_CREATED', 'REROUTE_UNAVAILABLE', 'OFF_ROUTE_DETECTED', 'DRIVER_ROUTE_CHANGED',
        'DISPATCH_ATTENTION', 'DRIVER_UNAVAILABLE', 'DISPATCH_REASSIGNED', 'TRIP_AUTO_STARTED',
        'DIRECT_ASSIGNMENT_DECLINED'));
