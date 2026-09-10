CREATE TABLE vehicle_tracking.stations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    address VARCHAR(255),
    latitude NUMERIC(8, 6) NOT NULL,
    longitude NUMERIC(9, 6) NOT NULL,
    checkin_radius_meters INTEGER NOT NULL DEFAULT 50,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_stations_name_not_blank
        CHECK (LENGTH(TRIM(name)) > 0),
    CONSTRAINT chk_stations_latitude
        CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_stations_longitude
        CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT chk_stations_checkin_radius
        CHECK (checkin_radius_meters BETWEEN 10 AND 1000)
);

CREATE INDEX idx_stations_active_name
    ON vehicle_tracking.stations (name, id)
    WHERE active = TRUE;

COMMENT ON TABLE vehicle_tracking.stations IS
    'Physical stations that can later be assigned START, STOP, or END roles per route';
COMMENT ON COLUMN vehicle_tracking.stations.latitude IS
    'WGS84 latitude used for map markers and future geofence distance calculations';
COMMENT ON COLUMN vehicle_tracking.stations.longitude IS
    'WGS84 longitude used for map markers and future geofence distance calculations';
COMMENT ON COLUMN vehicle_tracking.stations.checkin_radius_meters IS
    'Per-station geofence radius; stored now and consumed by the future automatic check-in feature';
COMMENT ON COLUMN vehicle_tracking.stations.active IS
    'Soft-delete flag; inactive stations are hidden from normal CRUD reads';
