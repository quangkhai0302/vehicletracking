ALTER TABLE vehicle_tracking.route_sections
    ADD COLUMN instructions JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD CONSTRAINT chk_route_section_instructions CHECK (jsonb_typeof(instructions) = 'array');
ALTER TABLE vehicle_tracking.trip_route_revision_sections
    ADD COLUMN instructions JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD CONSTRAINT chk_revision_section_instructions CHECK (jsonb_typeof(instructions) = 'array');
ALTER TABLE vehicle_tracking.trip_route_revisions DROP CONSTRAINT chk_trip_route_revision_reason;
ALTER TABLE vehicle_tracking.trip_route_revisions ADD CONSTRAINT chk_trip_route_revision_reason
    CHECK (reason_code IN ('ROAD_CLOSURE', 'TRAFFIC_DELAY', 'DRIVER_CHOICE'));
ALTER TABLE vehicle_tracking.trip_notifications DROP CONSTRAINT chk_trip_notification_type;
ALTER TABLE vehicle_tracking.trip_notifications ADD CONSTRAINT chk_trip_notification_type
    CHECK (type IN ('REROUTE_CREATED', 'REROUTE_UNAVAILABLE', 'OFF_ROUTE_DETECTED', 'DRIVER_ROUTE_CHANGED'));
