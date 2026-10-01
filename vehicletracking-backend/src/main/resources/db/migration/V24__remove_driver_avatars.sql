ALTER TABLE vehicle_tracking.drivers
    DROP COLUMN IF EXISTS avatar_data,
    DROP COLUMN IF EXISTS avatar_content_type;
