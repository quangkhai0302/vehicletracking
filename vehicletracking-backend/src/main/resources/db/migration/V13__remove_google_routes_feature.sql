-- Feature 018 was withdrawn. V12 remains immutable because it may already be
-- recorded by Flyway, while this migration restores the HERE-only schema.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM vehicle_tracking.routes
        WHERE routing_provider <> 'HERE'
    ) THEN
        RAISE EXCEPTION
            'Cannot remove Feature 018 while Google routes exist';
    END IF;
END
$$;

ALTER TABLE vehicle_tracking.routes DROP CONSTRAINT chk_routes_transport_mode;
ALTER TABLE vehicle_tracking.routes ADD CONSTRAINT chk_routes_transport_mode
    CHECK (transport_mode IN ('CAR', 'MOTORCYCLE'));

ALTER TABLE vehicle_tracking.routes DROP CONSTRAINT chk_routes_routing_provider;
ALTER TABLE vehicle_tracking.routes ADD CONSTRAINT chk_routes_routing_provider
    CHECK (routing_provider = 'HERE');

ALTER TABLE vehicle_tracking.routes DROP CONSTRAINT chk_routes_geometry_version;
ALTER TABLE vehicle_tracking.routes
    DROP COLUMN provider_content_expires_at,
    DROP COLUMN geometry_version;

ALTER TABLE vehicle_tracking.route_sections DROP CONSTRAINT chk_route_sections_polyline_encoding;
ALTER TABLE vehicle_tracking.route_sections
    DROP COLUMN traffic_intervals,
    DROP COLUMN polyline_encoding;

ALTER TABLE vehicle_tracking.trip_route_revision_sections DROP CONSTRAINT chk_revision_sections_polyline_encoding;
ALTER TABLE vehicle_tracking.trip_route_revision_sections
    DROP COLUMN traffic_intervals,
    DROP COLUMN polyline_encoding;
