ALTER TABLE vehicle_tracking.trip_turnaround_plans
    ADD COLUMN depot_revision BIGINT;

ALTER TABLE vehicle_tracking.trip_turnaround_plans
    ADD CONSTRAINT ck_turnaround_plan_depot_revision
    CHECK (depot_revision IS NULL OR depot_revision >= 0);
