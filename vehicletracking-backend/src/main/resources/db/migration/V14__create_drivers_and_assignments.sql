CREATE TABLE vehicle_tracking.drivers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    license_number VARCHAR(50) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_drivers_license_number UNIQUE (license_number),
    CONSTRAINT chk_drivers_name CHECK (LENGTH(TRIM(full_name)) BETWEEN 1 AND 100),
    CONSTRAINT chk_drivers_phone CHECK (phone_number ~ '^[+]?[0-9][0-9 .()-]{6,19}$'),
    CONSTRAINT chk_drivers_license CHECK (license_number ~ '^[A-Z0-9.-]{1,50}$')
);

ALTER TABLE vehicle_tracking.vehicles
    ADD COLUMN driver_id BIGINT REFERENCES vehicle_tracking.drivers(id) ON DELETE RESTRICT;

CREATE INDEX idx_vehicles_driver ON vehicle_tracking.vehicles(driver_id);
CREATE UNIQUE INDEX uq_active_vehicles_driver
    ON vehicle_tracking.vehicles(driver_id)
    WHERE driver_id IS NOT NULL AND active = TRUE;

ALTER TABLE vehicle_tracking.trips
    ADD COLUMN driver_id BIGINT REFERENCES vehicle_tracking.drivers(id) ON DELETE RESTRICT,
    ADD COLUMN driver_name_snapshot VARCHAR(100),
    ADD COLUMN driver_phone_snapshot VARCHAR(20),
    ADD COLUMN driver_license_number_snapshot VARCHAR(50),
    ADD CONSTRAINT chk_trips_driver_snapshot CHECK (
        (driver_id IS NULL
            AND driver_name_snapshot IS NULL
            AND driver_phone_snapshot IS NULL
            AND driver_license_number_snapshot IS NULL)
        OR
        (driver_id IS NOT NULL
            AND driver_name_snapshot IS NOT NULL
            AND driver_phone_snapshot IS NOT NULL
            AND driver_license_number_snapshot IS NOT NULL)
    );

CREATE INDEX idx_trips_driver_schedule
    ON vehicle_tracking.trips(driver_id, scheduled_departure_at DESC, id DESC);
CREATE UNIQUE INDEX uq_trips_running_driver
    ON vehicle_tracking.trips(driver_id)
    WHERE driver_id IS NOT NULL AND status = 'IN_PROGRESS';

COMMENT ON TABLE vehicle_tracking.drivers IS
    'Driver catalog. Rows are deactivated rather than deleted to preserve operational history.';
COMMENT ON COLUMN vehicle_tracking.vehicles.driver_id IS
    'Current optional driver assignment; trip assignments are stored independently.';
COMMENT ON COLUMN vehicle_tracking.trips.driver_name_snapshot IS
    'Immutable display name captured when the scheduled trip driver assignment is set.';
