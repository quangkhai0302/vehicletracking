ALTER TABLE vehicle_tracking.driver_dispatch_inbox
    ADD COLUMN dismissed_at TIMESTAMPTZ;

CREATE INDEX idx_driver_dispatch_inbox_visible_latest
    ON vehicle_tracking.driver_dispatch_inbox(recipient_driver_id, created_at DESC, id DESC)
    WHERE dismissed_at IS NULL;
