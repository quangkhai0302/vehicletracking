-- Preserve the last pre-047 attempt for existing trips. New trips keep the
-- default zero marker; replay increments attempt_number without changing it.
ALTER TABLE vehicle_tracking.trips
    ADD COLUMN turnaround_legacy_max_attempt INTEGER NOT NULL DEFAULT 0
        CHECK (turnaround_legacy_max_attempt >= 0);

UPDATE vehicle_tracking.trips
SET turnaround_legacy_max_attempt = attempt_number;

-- A plan id alone is not enough to prove that a confirmation belongs to the
-- same trip attempt. These unique keys make the relationship expressible as a
-- composite foreign key while retaining the existing scalar indexes.
ALTER TABLE vehicle_tracking.trip_turnaround_plans
    ADD CONSTRAINT uq_turnaround_plan_identity UNIQUE (id, trip_id, attempt_number);

ALTER TABLE vehicle_tracking.depot_return_records
    ADD CONSTRAINT uq_depot_return_identity UNIQUE (id, trip_id, attempt_number);

ALTER TABLE vehicle_tracking.resource_depot_confirmations
    ADD CONSTRAINT fk_confirmation_plan_identity
    FOREIGN KEY (plan_id, trip_id, attempt_number)
    REFERENCES vehicle_tracking.trip_turnaround_plans (id, trip_id, attempt_number)
    NOT VALID;

ALTER TABLE vehicle_tracking.resource_depot_confirmations
    ADD CONSTRAINT fk_confirmation_return_identity
    FOREIGN KEY (depot_return_record_id, trip_id, attempt_number)
    REFERENCES vehicle_tracking.depot_return_records (id, trip_id, attempt_number)
    NOT VALID;

-- V26 rows predate depot snapshots on the 047 ledger. Keep those historical
-- rows readable, but require snapshots on every new row through the trigger.
ALTER TABLE vehicle_tracking.resource_depot_confirmations
    ADD COLUMN depot_id SMALLINT REFERENCES vehicle_tracking.operating_depot(id) ON DELETE RESTRICT,
    ADD COLUMN depot_name_snapshot VARCHAR(100),
    ADD COLUMN depot_address_snapshot VARCHAR(255);

ALTER TABLE vehicle_tracking.resource_depot_confirmations
    ADD CONSTRAINT ck_confirmation_depot_snapshot CHECK (
        (depot_id IS NULL AND depot_name_snapshot IS NULL AND depot_address_snapshot IS NULL)
        OR (depot_id IS NOT NULL
            AND length(btrim(depot_name_snapshot)) BETWEEN 1 AND 100
            AND length(btrim(depot_address_snapshot)) BETWEEN 1 AND 255)
    );

ALTER TABLE vehicle_tracking.resource_depot_confirmations
    DROP CONSTRAINT ck_confirmation_source_kind;

ALTER TABLE vehicle_tracking.resource_depot_confirmations
    ADD CONSTRAINT ck_confirmation_source_kind CHECK (
        source = 'BASELINE_MANUAL'
        OR (source = 'SIMULATED' AND execution_kind = 'SIMULATION')
        OR (source = 'TRIP_RETURN_MANUAL' AND execution_kind = 'REAL')
    );

CREATE OR REPLACE FUNCTION vehicle_tracking.validate_resource_confirmation_identity()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    plan_trip BIGINT;
    plan_attempt INTEGER;
    plan_vehicle BIGINT;
    plan_driver BIGINT;
    return_trip BIGINT;
    return_attempt INTEGER;
    return_vehicle BIGINT;
BEGIN
    IF NEW.depot_id IS NULL OR NEW.depot_name_snapshot IS NULL OR NEW.depot_address_snapshot IS NULL THEN
        RAISE EXCEPTION 'new resource confirmations require a depot snapshot';
    END IF;
    IF NEW.plan_id IS NOT NULL THEN
        SELECT p.trip_id, p.attempt_number, p.vehicle_id, p.driver_id
          INTO plan_trip, plan_attempt, plan_vehicle, plan_driver
          FROM vehicle_tracking.trip_turnaround_plans p WHERE p.id = NEW.plan_id;
        IF plan_trip IS NULL OR NEW.trip_id IS DISTINCT FROM plan_trip
           OR NEW.attempt_number IS DISTINCT FROM plan_attempt
           OR (NEW.resource_kind = 'VEHICLE' AND NEW.vehicle_id IS DISTINCT FROM plan_vehicle)
           OR (NEW.resource_kind = 'DRIVER' AND NEW.driver_id IS DISTINCT FROM plan_driver)
           OR NEW.execution_kind IS DISTINCT FROM (SELECT execution_kind FROM vehicle_tracking.trip_turnaround_plans WHERE id = NEW.plan_id)
        THEN RAISE EXCEPTION 'resource confirmation does not match its turnaround plan'; END IF;
    END IF;
    IF NEW.depot_return_record_id IS NOT NULL THEN
        SELECT r.trip_id, r.attempt_number, r.confirmed_vehicle_id
          INTO return_trip, return_attempt, return_vehicle
          FROM vehicle_tracking.depot_return_records r WHERE r.id = NEW.depot_return_record_id;
        IF NEW.trip_id IS DISTINCT FROM return_trip OR NEW.attempt_number IS DISTINCT FROM return_attempt
           OR NEW.resource_kind <> 'VEHICLE' OR NEW.vehicle_id IS DISTINCT FROM return_vehicle
        THEN RAISE EXCEPTION 'resource confirmation does not match its canonical return'; END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_resource_confirmation_identity
    BEFORE INSERT ON vehicle_tracking.resource_depot_confirmations
    FOR EACH ROW EXECUTE FUNCTION vehicle_tracking.validate_resource_confirmation_identity();
