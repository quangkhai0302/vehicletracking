ALTER TABLE vehicle_tracking.drivers
    ADD COLUMN avatar_data BYTEA,
    ADD COLUMN avatar_content_type VARCHAR(50);
