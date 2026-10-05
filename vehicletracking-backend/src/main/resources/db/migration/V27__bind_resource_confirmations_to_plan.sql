ALTER TABLE vehicle_tracking.resource_depot_confirmations
    ADD COLUMN plan_id BIGINT REFERENCES vehicle_tracking.trip_turnaround_plans(id) ON DELETE RESTRICT,
    ADD COLUMN depot_revision BIGINT NOT NULL DEFAULT 0 CHECK (depot_revision >= 0),
    ADD COLUMN depot_return_record_id BIGINT REFERENCES vehicle_tracking.depot_return_records(id) ON DELETE RESTRICT;

CREATE INDEX idx_confirmation_plan ON vehicle_tracking.resource_depot_confirmations(plan_id)
    WHERE plan_id IS NOT NULL;
CREATE INDEX idx_confirmation_return_record ON vehicle_tracking.resource_depot_confirmations(depot_return_record_id)
    WHERE depot_return_record_id IS NOT NULL;

ALTER TABLE vehicle_tracking.resource_depot_confirmations
    ADD CONSTRAINT ck_confirmation_vehicle_canonical_return CHECK (
        resource_kind <> 'VEHICLE' OR execution_kind <> 'REAL'
        OR source = 'BASELINE_MANUAL' OR depot_return_record_id IS NOT NULL
    ) NOT VALID;

-- V26 may already contain historical confirmations without an actor. Keep
-- those rows readable, while enforcing actor provenance for every new row.
ALTER TABLE vehicle_tracking.resource_depot_confirmations
    ADD CONSTRAINT ck_confirmation_actor_required
    CHECK (confirmed_by_account_id IS NOT NULL) NOT VALID;

ALTER TABLE vehicle_tracking.trip_turnaround_plans
    DROP CONSTRAINT ck_turnaround_driver_fields;

ALTER TABLE vehicle_tracking.trip_turnaround_plans
    ADD CONSTRAINT ck_turnaround_driver_fields CHECK (
        (driver_id IS NULL AND driver_terminal_to_depot_seconds IS NOT NULL AND driver_planned_return_at IS NULL)
        OR (driver_id IS NOT NULL AND driver_terminal_to_depot_seconds IS NOT NULL AND driver_planned_return_at IS NOT NULL)
    );

-- Preserve historical NOT_REQUIRED rows, but prevent the 047 write model from
-- creating a new exemption or changing another mode into one.
CREATE OR REPLACE FUNCTION vehicle_tracking.reject_new_not_required_return()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.mode = 'NOT_REQUIRED' AND (TG_OP = 'INSERT' OR OLD.mode <> 'NOT_REQUIRED') THEN
        RAISE EXCEPTION 'NOT_REQUIRED is legacy-only';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_depot_return_legacy_not_required
    BEFORE INSERT OR UPDATE ON vehicle_tracking.depot_return_records
    FOR EACH ROW EXECUTE FUNCTION vehicle_tracking.reject_new_not_required_return();
