ALTER TABLE vehicle_tracking.operating_depot
    ADD COLUMN latitude NUMERIC(8, 6),
    ADD COLUMN longitude NUMERIC(9, 6),
    ADD CONSTRAINT operating_depot_location_check CHECK (
        (latitude IS NULL AND longitude IS NULL)
        OR (latitude IS NOT NULL AND longitude IS NOT NULL
            AND latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)
    );
