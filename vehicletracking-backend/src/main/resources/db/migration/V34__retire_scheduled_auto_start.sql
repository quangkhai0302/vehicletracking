-- Stop policy-driven departure without deleting prior confirmations or assignment history.
UPDATE vehicle_tracking.trip_schedules
SET start_mode = 'MANUAL', version = version + 1, updated_at = CURRENT_TIMESTAMP
WHERE start_mode <> 'MANUAL';

-- READY snapshots are historical facts after retirement, even when the dispatch is closed.
ALTER TABLE vehicle_tracking.trip_dispatches
    DROP CONSTRAINT chk_trip_dispatch_ready_state;
ALTER TABLE vehicle_tracking.trip_dispatches
    ADD CONSTRAINT chk_trip_dispatch_ready_state CHECK (
        (state = 'READY' AND ready_at IS NOT NULL)
        OR (state = 'CLOSED')
        OR (state <> 'READY' AND ready_at IS NULL)
    );

WITH retired AS (
    UPDATE vehicle_tracking.trip_dispatches
    SET state = 'CLOSED', next_action_at = NULL, attention_code = NULL,
        revision = revision + 1, updated_at = CURRENT_TIMESTAMP
    WHERE state <> 'CLOSED' OR next_action_at IS NOT NULL OR attention_code IS NOT NULL
    RETURNING trip_id, revision
)
INSERT INTO vehicle_tracking.trip_dispatch_events
    (trip_id, revision, kind, actor_kind, reason, created_at)
SELECT trip_id, revision, 'CLOSED', 'SYSTEM',
       'Gỡ tự khởi hành theo lịch; sử dụng thao tác bắt đầu chuyến thông thường.', CURRENT_TIMESTAMP
FROM retired;

ALTER TABLE vehicle_tracking.trip_schedules
    ADD CONSTRAINT chk_schedule_auto_start_retired CHECK (start_mode = 'MANUAL');
ALTER TABLE vehicle_tracking.trip_dispatches
    ADD CONSTRAINT chk_dispatch_auto_start_retired CHECK (state = 'CLOSED' AND next_action_at IS NULL);
