-- Feature 010: soft-delete support for route management.
ALTER TABLE vehicle_tracking.routes
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
CREATE INDEX idx_routes_active_created
    ON vehicle_tracking.routes (active, created_at DESC, id DESC);
