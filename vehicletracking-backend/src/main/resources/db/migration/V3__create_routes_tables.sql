-- V3: Create routes, route stops, and route sections tables
-- Purpose: Support immutable route snapshots calculated from ordered active stations using HERE Routing API v8

-- 1. Routes table: Stores top-level route metadata, provider used, and aggregated distance/duration metrics
CREATE TABLE vehicle_tracking.routes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    transport_mode VARCHAR(20) NOT NULL,
    routing_provider VARCHAR(20) NOT NULL,
    total_distance_meters BIGINT NOT NULL,
    estimated_travel_duration_seconds BIGINT NOT NULL,
    base_travel_duration_seconds BIGINT NOT NULL,
    total_dwell_duration_seconds BIGINT NOT NULL,
    estimated_trip_duration_seconds BIGINT NOT NULL,
    estimated_departure_at TIMESTAMPTZ NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_routes_name_non_blank CHECK (LENGTH(TRIM(name)) > 0),
    CONSTRAINT chk_routes_transport_mode CHECK (transport_mode IN ('CAR')),
    CONSTRAINT chk_routes_routing_provider CHECK (routing_provider IN ('HERE')),
    CONSTRAINT chk_routes_total_distance CHECK (total_distance_meters >= 0),
    CONSTRAINT chk_routes_estimated_travel_duration CHECK (estimated_travel_duration_seconds >= 0),
    CONSTRAINT chk_routes_base_travel_duration CHECK (base_travel_duration_seconds >= 0),
    CONSTRAINT chk_routes_total_dwell_duration CHECK (total_dwell_duration_seconds >= 0),
    CONSTRAINT chk_routes_estimated_trip_duration CHECK (estimated_trip_duration_seconds >= estimated_travel_duration_seconds)
);

CREATE INDEX idx_routes_created_at ON vehicle_tracking.routes (created_at DESC, id DESC);

COMMENT ON TABLE vehicle_tracking.routes IS 'Stores immutable route snapshots calculated from ordered active stations';
COMMENT ON COLUMN vehicle_tracking.routes.estimated_trip_duration_seconds IS 'Total estimated duration = estimated_travel_duration_seconds + total_dwell_duration_seconds';
COMMENT ON COLUMN vehicle_tracking.routes.calculated_at IS 'Timestamp when the routing provider completed calculation';

-- 2. Route stops table: Stores ordered sequence of stations and immutable coordinate/name snapshots
CREATE TABLE vehicle_tracking.route_stops (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    route_id BIGINT NOT NULL,
    station_id BIGINT NOT NULL,
    sequence_number INTEGER NOT NULL,
    station_name_snapshot VARCHAR(150) NOT NULL,
    latitude_snapshot NUMERIC(8,6) NOT NULL,
    longitude_snapshot NUMERIC(9,6) NOT NULL,
    dwell_duration_seconds INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT fk_route_stops_route FOREIGN KEY (route_id) REFERENCES vehicle_tracking.routes (id) ON DELETE CASCADE,
    CONSTRAINT fk_route_stops_station FOREIGN KEY (station_id) REFERENCES vehicle_tracking.stations (id) ON DELETE RESTRICT,
    CONSTRAINT chk_route_stops_name_non_blank CHECK (LENGTH(TRIM(station_name_snapshot)) > 0),
    CONSTRAINT chk_route_stops_latitude CHECK (latitude_snapshot BETWEEN -90.0 AND 90.0),
    CONSTRAINT chk_route_stops_longitude CHECK (longitude_snapshot BETWEEN -180.0 AND 180.0),
    CONSTRAINT chk_route_stops_dwell_duration CHECK (dwell_duration_seconds BETWEEN 0 AND 3600),
    CONSTRAINT chk_route_stops_sequence_positive CHECK (sequence_number >= 1),
    CONSTRAINT uq_route_stops_route_sequence UNIQUE (route_id, sequence_number)
);

CREATE INDEX idx_route_stops_station_id ON vehicle_tracking.route_stops (station_id);

COMMENT ON TABLE vehicle_tracking.route_stops IS 'Stores the ordered list of stops for a route with station name and coordinate snapshots';
COMMENT ON COLUMN vehicle_tracking.route_stops.sequence_number IS '1-based stop sequence. sequence 1 is START, sequence N is END, intermediate are STOP';
COMMENT ON COLUMN vehicle_tracking.route_stops.dwell_duration_seconds IS 'Planned dwell time at intermediate stops; must be 0 for START and END';

-- 3. Route sections table: Stores normalized geometry and metrics for each segment of the route
CREATE TABLE vehicle_tracking.route_sections (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    route_id BIGINT NOT NULL,
    section_sequence INTEGER NOT NULL,
    destination_stop_sequence INTEGER NOT NULL,
    encoded_polyline TEXT NOT NULL,
    distance_meters BIGINT NOT NULL,
    travel_duration_seconds BIGINT NOT NULL,
    base_travel_duration_seconds BIGINT NOT NULL,

    CONSTRAINT fk_route_sections_route FOREIGN KEY (route_id) REFERENCES vehicle_tracking.routes (id) ON DELETE CASCADE,
    CONSTRAINT chk_route_sections_sequence_positive CHECK (section_sequence >= 1),
    CONSTRAINT uq_route_sections_route_sequence UNIQUE (route_id, section_sequence),
    CONSTRAINT chk_route_sections_dest_stop_min CHECK (destination_stop_sequence >= 2),
    CONSTRAINT fk_route_sections_destination_stop FOREIGN KEY (route_id, destination_stop_sequence)
        REFERENCES vehicle_tracking.route_stops (route_id, sequence_number) ON DELETE CASCADE
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT chk_route_sections_polyline_non_blank CHECK (LENGTH(TRIM(encoded_polyline)) > 0),
    CONSTRAINT chk_route_sections_distance CHECK (distance_meters >= 0),
    CONSTRAINT chk_route_sections_travel_duration CHECK (travel_duration_seconds >= 0),
    CONSTRAINT chk_route_sections_base_travel_duration CHECK (base_travel_duration_seconds >= 0)
);

CREATE INDEX idx_route_sections_route_destination ON vehicle_tracking.route_sections (route_id, destination_stop_sequence, section_sequence);

COMMENT ON TABLE vehicle_tracking.route_sections IS 'Stores normalized section geometry and metrics from the routing provider';
COMMENT ON COLUMN vehicle_tracking.route_sections.destination_stop_sequence IS 'Sequence number of the stop that this section leads to';
