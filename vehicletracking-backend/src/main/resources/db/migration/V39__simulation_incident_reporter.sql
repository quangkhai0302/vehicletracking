ALTER TABLE vehicle_tracking.simulation_incidents
    ADD COLUMN reported_by_driver_id BIGINT REFERENCES vehicle_tracking.drivers(id) ON DELETE RESTRICT;

CREATE INDEX idx_simulation_incidents_reported_by_driver
    ON vehicle_tracking.simulation_incidents (reported_by_driver_id, created_at DESC)
    WHERE reported_by_driver_id IS NOT NULL;

COMMENT ON COLUMN vehicle_tracking.simulation_incidents.reported_by_driver_id IS
    'Driver account profile that reported the incident; NULL is reserved for legacy incidents created before driver reporting.';
