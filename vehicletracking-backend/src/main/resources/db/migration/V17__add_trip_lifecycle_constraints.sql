ALTER TABLE vehicle_tracking.trips
    ADD COLUMN cancellation_reason VARCHAR(500);

ALTER TABLE vehicle_tracking.trips
    ADD CONSTRAINT chk_trips_cancellation_reason CHECK (
        (status = 'CANCELLED' AND (cancellation_reason IS NULL OR LENGTH(TRIM(cancellation_reason)) > 0))
        OR (status <> 'CANCELLED' AND cancellation_reason IS NULL)
    );

COMMENT ON COLUMN vehicle_tracking.trips.cancellation_reason IS
    'Operator-provided reason for cancelling a scheduled or in-progress trip.';
