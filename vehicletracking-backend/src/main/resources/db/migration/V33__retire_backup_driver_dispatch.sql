-- Retire the active backup policy while retaining pools, offers and event history.
ALTER TABLE vehicle_tracking.trip_dispatches
    DROP CONSTRAINT trip_dispatches_attention_code_check;
ALTER TABLE vehicle_tracking.trip_dispatches
    ADD CONSTRAINT trip_dispatches_attention_code_check CHECK (attention_code IN
        ('DRIVER_NOT_READY', 'NO_DRIVER', 'RESOURCE_UNAVAILABLE', 'START_FAILED',
         'SCHEDULE_DISABLED', 'WINDOW_EXPIRED', 'NO_BACKUP'));

UPDATE vehicle_tracking.trip_schedules
SET backup_enabled = FALSE, version = version + 1, updated_at = CURRENT_TIMESTAMP
WHERE backup_enabled;

UPDATE vehicle_tracking.trip_dispatch_offers
SET status = 'CANCELLED', responded_at = CURRENT_TIMESTAMP,
    response_reason = 'Chức năng tài xế dự phòng đã được gỡ.'
WHERE status = 'PENDING';

WITH retired AS (
    UPDATE vehicle_tracking.trip_dispatches d
    SET backup_enabled = FALSE,
        state = CASE WHEN d.state IN ('SEARCH_WAIT', 'OFFER_PENDING') THEN
            CASE WHEN t.status <> 'SCHEDULED' THEN 'CLOSED'
                 WHEN NOT s.enabled THEN 'ATTENTION'
                 WHEN t.driver_id IS NULL THEN 'ATTENTION'
                 ELSE 'WAITING_READY' END
            ELSE d.state END,
        attention_code = CASE WHEN d.state IN ('SEARCH_WAIT', 'OFFER_PENDING') THEN
            CASE WHEN t.status <> 'SCHEDULED' THEN NULL
                 WHEN NOT s.enabled THEN 'SCHEDULE_DISABLED'
                 WHEN t.driver_id IS NULL THEN 'NO_DRIVER'
                 ELSE NULL END
            WHEN d.attention_code = 'NO_BACKUP' THEN 'NO_DRIVER'
            ELSE d.attention_code END,
        next_action_at = CASE WHEN d.state IN ('SEARCH_WAIT', 'OFFER_PENDING') THEN
            CASE WHEN t.status = 'SCHEDULED' AND s.enabled AND t.driver_id IS NOT NULL
                 THEN t.scheduled_departure_at ELSE NULL END
            ELSE d.next_action_at END,
        revision = d.revision + 1, updated_at = CURRENT_TIMESTAMP
    FROM vehicle_tracking.trips t, vehicle_tracking.trip_schedules s
    WHERE d.trip_id = t.id AND t.schedule_id = s.id
      AND (d.backup_enabled OR d.state IN ('SEARCH_WAIT', 'OFFER_PENDING')
           OR d.attention_code = 'NO_BACKUP')
    RETURNING d.trip_id, d.revision
)
INSERT INTO vehicle_tracking.trip_dispatch_events
    (trip_id, revision, kind, actor_kind, reason, created_at)
SELECT trip_id, revision, 'POLICY_CHANGED', 'SYSTEM',
       'Gỡ tài xế dự phòng; chuyến thiếu tài xế cần điều phối viên phân công.', CURRENT_TIMESTAMP
FROM retired;

ALTER TABLE vehicle_tracking.trip_dispatches
    DROP CONSTRAINT trip_dispatches_attention_code_check,
    DROP CONSTRAINT trip_dispatches_state_check;
ALTER TABLE vehicle_tracking.trip_dispatches
    ALTER COLUMN backup_enabled SET DEFAULT FALSE,
    ADD CONSTRAINT trip_dispatches_attention_code_check CHECK (attention_code IN
        ('DRIVER_NOT_READY', 'NO_DRIVER', 'RESOURCE_UNAVAILABLE', 'START_FAILED',
         'SCHEDULE_DISABLED', 'WINDOW_EXPIRED')),
    ADD CONSTRAINT trip_dispatches_state_check CHECK (state IN
        ('MANUAL', 'WAITING_READY', 'READY', 'ATTENTION', 'STARTED', 'CLOSED')),
    ADD CONSTRAINT chk_trip_dispatch_backup_retired CHECK (backup_enabled = FALSE);
ALTER TABLE vehicle_tracking.trip_schedules
    ADD CONSTRAINT chk_trip_schedule_backup_retired CHECK (backup_enabled = FALSE);
