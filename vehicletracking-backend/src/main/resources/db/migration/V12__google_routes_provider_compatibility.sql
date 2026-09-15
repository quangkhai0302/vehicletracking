-- Feature 018: preserve legacy HERE routes while allowing explicitly encoded Google route geometry.
ALTER TABLE vehicle_tracking.routes DROP CONSTRAINT chk_routes_transport_mode;
ALTER TABLE vehicle_tracking.routes ADD CONSTRAINT chk_routes_transport_mode
    CHECK (transport_mode IN ('CAR', 'MOTORCYCLE'));

ALTER TABLE vehicle_tracking.routes DROP CONSTRAINT chk_routes_routing_provider;
ALTER TABLE vehicle_tracking.routes ADD CONSTRAINT chk_routes_routing_provider
    CHECK (routing_provider IN ('HERE', 'GOOGLE'));

ALTER TABLE vehicle_tracking.routes
    ADD COLUMN geometry_version BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN provider_content_expires_at TIMESTAMPTZ;
ALTER TABLE vehicle_tracking.routes ADD CONSTRAINT chk_routes_geometry_version CHECK (geometry_version > 0);

ALTER TABLE vehicle_tracking.route_sections
    ADD COLUMN polyline_encoding VARCHAR(40) NOT NULL DEFAULT 'HERE_FLEXIBLE_POLYLINE',
    ADD COLUMN traffic_intervals TEXT;
ALTER TABLE vehicle_tracking.route_sections ADD CONSTRAINT chk_route_sections_polyline_encoding
    CHECK (polyline_encoding IN ('HERE_FLEXIBLE_POLYLINE', 'GOOGLE_ENCODED_POLYLINE'));

ALTER TABLE vehicle_tracking.trip_route_revision_sections
    ADD COLUMN polyline_encoding VARCHAR(40) NOT NULL DEFAULT 'HERE_FLEXIBLE_POLYLINE',
    ADD COLUMN traffic_intervals TEXT;
ALTER TABLE vehicle_tracking.trip_route_revision_sections ADD CONSTRAINT chk_revision_sections_polyline_encoding
    CHECK (polyline_encoding IN ('HERE_FLEXIBLE_POLYLINE', 'GOOGLE_ENCODED_POLYLINE'));

COMMENT ON COLUMN vehicle_tracking.routes.geometry_version IS
    'Monotonic identity for geometry-dependent ETA, simulation and reroute data';
COMMENT ON COLUMN vehicle_tracking.routes.provider_content_expires_at IS
    'Expiry boundary for provider-derived route content when provider terms require one';
COMMENT ON COLUMN vehicle_tracking.route_sections.polyline_encoding IS
    'Explicit codec; existing rows are backfilled as HERE Flexible Polyline';
