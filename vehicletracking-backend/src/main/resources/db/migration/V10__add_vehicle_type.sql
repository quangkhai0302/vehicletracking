ALTER TABLE vehicle_tracking.vehicles
    ADD COLUMN vehicle_type VARCHAR(20) NOT NULL DEFAULT 'CAR';

ALTER TABLE vehicle_tracking.vehicles
    ADD CONSTRAINT chk_vehicles_type CHECK (vehicle_type IN ('CAR', 'MOTORCYCLE'));

COMMENT ON COLUMN vehicle_tracking.vehicles.vehicle_type IS
    'Vehicle presentation category; CAR or MOTORCYCLE. Does not select HERE routing transport mode.';
