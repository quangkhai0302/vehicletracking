-- Historical clocks and first-play metadata cannot be reconstructed safely.
ALTER TABLE vehicle_tracking.simulation_runs
    ADD COLUMN virtual_elapsed_seconds DOUBLE PRECISION,
    ADD COLUMN planned_duration_seconds DOUBLE PRECISION,
    ADD COLUMN planned_distance_meters DOUBLE PRECISION,
    ADD COLUMN attempt_started_at TIMESTAMPTZ,
    ADD COLUMN report_vehicle_id BIGINT,
    ADD COLUMN report_vehicle_plate VARCHAR(20),
    ADD COLUMN report_driver_id BIGINT,
    ADD COLUMN report_driver_name VARCHAR(100),
    ADD COLUMN report_route_id BIGINT,
    ADD COLUMN report_route_name VARCHAR(150),
    ADD COLUMN scenario VARCHAR(20);
ALTER TABLE vehicle_tracking.simulation_runs
    ADD CONSTRAINT chk_simulation_runs_virtual_clock CHECK (virtual_elapsed_seconds IS NULL OR (virtual_elapsed_seconds >= 0 AND virtual_elapsed_seconds < 'Infinity'::double precision)),
    ADD CONSTRAINT chk_simulation_runs_report_duration CHECK (planned_duration_seconds IS NULL OR (planned_duration_seconds >= 0 AND planned_duration_seconds < 'Infinity'::double precision)),
    ADD CONSTRAINT chk_simulation_runs_report_distance CHECK (planned_distance_meters IS NULL OR (planned_distance_meters >= 0 AND planned_distance_meters < 'Infinity'::double precision)),
    ADD CONSTRAINT chk_simulation_runs_scenario CHECK (scenario IS NULL OR scenario IN ('CURRENT_TRAFFIC','NORMAL','CONGESTION','BLOCKED','OFF_ROUTE'));
CREATE INDEX idx_simulation_runs_report_started ON vehicle_tracking.simulation_runs(attempt_started_at);
ALTER TABLE vehicle_tracking.simulation_attempts
    ADD COLUMN virtual_elapsed_seconds DOUBLE PRECISION,
    ADD COLUMN planned_duration_seconds DOUBLE PRECISION,
    ADD COLUMN planned_distance_meters DOUBLE PRECISION,
    ADD COLUMN attempt_started_at TIMESTAMPTZ,
    ADD COLUMN report_vehicle_id BIGINT,
    ADD COLUMN report_vehicle_plate VARCHAR(20),
    ADD COLUMN report_driver_id BIGINT,
    ADD COLUMN report_driver_name VARCHAR(100),
    ADD COLUMN report_route_id BIGINT,
    ADD COLUMN report_route_name VARCHAR(150),
    ADD COLUMN scenario VARCHAR(20);
ALTER TABLE vehicle_tracking.simulation_attempts
    ADD CONSTRAINT chk_simulation_attempts_virtual_clock CHECK (virtual_elapsed_seconds IS NULL OR (virtual_elapsed_seconds >= 0 AND virtual_elapsed_seconds < 'Infinity'::double precision)),
    ADD CONSTRAINT chk_simulation_attempts_report_duration CHECK (planned_duration_seconds IS NULL OR (planned_duration_seconds >= 0 AND planned_duration_seconds < 'Infinity'::double precision)),
    ADD CONSTRAINT chk_simulation_attempts_report_distance CHECK (planned_distance_meters IS NULL OR (planned_distance_meters >= 0 AND planned_distance_meters < 'Infinity'::double precision)),
    ADD CONSTRAINT chk_simulation_attempts_scenario CHECK (scenario IS NULL OR scenario IN ('CURRENT_TRAFFIC','NORMAL','CONGESTION','BLOCKED','OFF_ROUTE'));
CREATE INDEX idx_simulation_attempts_report_started ON vehicle_tracking.simulation_attempts(attempt_started_at);

ALTER TABLE vehicle_tracking.trip_notifications
    ADD COLUMN attempt_number INTEGER,
    ADD COLUMN source VARCHAR(20),
    ADD CONSTRAINT chk_notification_attempt CHECK (attempt_number IS NULL OR attempt_number > 0),
    ADD CONSTRAINT chk_notification_source CHECK (source IS NULL OR source IN ('GPS','SIMULATOR'));
-- Revision anchors are the only unambiguous simulator provenance for existing events.
UPDATE vehicle_tracking.trip_notifications n
SET attempt_number=r.simulation_attempt_number, source='SIMULATOR'
FROM vehicle_tracking.trip_route_revisions r
WHERE n.revision_id=r.id AND r.simulation_attempt_number IS NOT NULL;
CREATE INDEX idx_notifications_sim_attempt ON vehicle_tracking.trip_notifications(trip_id,attempt_number,source,type);

