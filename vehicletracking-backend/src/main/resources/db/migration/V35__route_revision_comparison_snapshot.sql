ALTER TABLE vehicle_tracking.trip_route_revisions ADD COLUMN comparison_snapshot JSONB;

-- Legacy rows deliberately remain NULL; no guessed history is backfilled.
ALTER TABLE vehicle_tracking.trip_route_revisions ADD CONSTRAINT chk_route_comparison_snapshot CHECK (
    comparison_snapshot IS NULL OR (
        jsonb_typeof(comparison_snapshot) = 'object'
        AND jsonb_typeof(comparison_snapshot->'attemptNumber') = 'number'
        AND (comparison_snapshot->>'attemptNumber')::numeric >= 1
        AND (comparison_snapshot->>'attemptNumber')::numeric = trunc((comparison_snapshot->>'attemptNumber')::numeric)
        AND comparison_snapshot ? 'previousRevisionId'
        AND (comparison_snapshot->'previousRevisionId' = 'null'::jsonb OR (
            jsonb_typeof(comparison_snapshot->'previousRevisionId') = 'number'
            AND (comparison_snapshot->>'previousRevisionId')::numeric >= 1
            AND (comparison_snapshot->>'previousRevisionId')::numeric = trunc((comparison_snapshot->>'previousRevisionId')::numeric)))
        AND jsonb_typeof(comparison_snapshot->'anchor') = 'object'
        AND jsonb_typeof(comparison_snapshot->'anchor'->'latitude') = 'number'
        AND jsonb_typeof(comparison_snapshot->'anchor'->'longitude') = 'number'
        AND (comparison_snapshot->'anchor'->>'latitude')::numeric BETWEEN -90 AND 90
        AND (comparison_snapshot->'anchor'->>'longitude')::numeric BETWEEN -180 AND 180
        AND jsonb_typeof(comparison_snapshot->'before') = 'object'
        AND jsonb_typeof(comparison_snapshot->'after') = 'object'
        AND jsonb_typeof(comparison_snapshot->'before'->'encodedPolylines') = 'array'
        AND jsonb_typeof(comparison_snapshot->'after'->'encodedPolylines') = 'array'
        AND jsonb_array_length(comparison_snapshot->'before'->'encodedPolylines') > 0
        AND NOT jsonb_path_exists(comparison_snapshot->'before'->'encodedPolylines', '$[*] ? (@.type() != "string" || @ == "")')
        AND jsonb_array_length(comparison_snapshot->'after'->'encodedPolylines') > 0
        AND NOT jsonb_path_exists(comparison_snapshot->'after'->'encodedPolylines', '$[*] ? (@.type() != "string" || @ == "")')
        AND jsonb_typeof(comparison_snapshot->'before'->'distanceMeters') = 'number'
        AND jsonb_typeof(comparison_snapshot->'after'->'distanceMeters') = 'number'
        AND (comparison_snapshot->'before'->>'distanceMeters')::numeric >= 0
        AND (comparison_snapshot->'before'->>'distanceMeters')::numeric = trunc((comparison_snapshot->'before'->>'distanceMeters')::numeric)
        AND (comparison_snapshot->'after'->>'distanceMeters')::numeric >= 0
        AND (comparison_snapshot->'after'->>'distanceMeters')::numeric = trunc((comparison_snapshot->'after'->>'distanceMeters')::numeric)
        AND comparison_snapshot->'before' ? 'durationSeconds'
        AND comparison_snapshot->'after' ? 'durationSeconds'
        AND (comparison_snapshot->'before'->'durationSeconds' = 'null'::jsonb OR (
            jsonb_typeof(comparison_snapshot->'before'->'durationSeconds') = 'number'
            AND (comparison_snapshot->'before'->>'durationSeconds')::numeric >= 0
            AND (comparison_snapshot->'before'->>'durationSeconds')::numeric = trunc((comparison_snapshot->'before'->>'durationSeconds')::numeric)))
        AND (comparison_snapshot->'after'->'durationSeconds' = 'null'::jsonb OR (
            jsonb_typeof(comparison_snapshot->'after'->'durationSeconds') = 'number'
            AND (comparison_snapshot->'after'->>'durationSeconds')::numeric >= 0
            AND (comparison_snapshot->'after'->>'durationSeconds')::numeric = trunc((comparison_snapshot->'after'->>'durationSeconds')::numeric)))
    ) IS TRUE
);
