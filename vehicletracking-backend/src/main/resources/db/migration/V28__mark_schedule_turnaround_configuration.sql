-- V26 added turnaround scalar columns with compatibility defaults.  Those
-- defaults are not evidence that an existing schedule was configured, so keep
-- an explicit provenance marker for legacy rows.
ALTER TABLE vehicle_tracking.trip_schedules
    ADD COLUMN turnaround_configured BOOLEAN NOT NULL DEFAULT FALSE;

