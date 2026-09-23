-- Keep operational events available to historical reports after an operator
-- removes them from the notification inbox.
ALTER TABLE vehicle_tracking.trip_notifications
    ADD COLUMN dismissed_at TIMESTAMPTZ;

CREATE INDEX idx_trip_notifications_visible
    ON vehicle_tracking.trip_notifications (created_at DESC, id DESC)
    WHERE dismissed_at IS NULL;

CREATE INDEX idx_trip_notifications_visible_unread
    ON vehicle_tracking.trip_notifications (created_at DESC, id DESC)
    WHERE read_at IS NULL AND dismissed_at IS NULL;
