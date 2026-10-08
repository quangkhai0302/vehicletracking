ALTER TABLE vehicle_tracking.simulation_incidents ADD COLUMN resolution_note VARCHAR(500);

-- A report and its resolution are separate notifications about the same incident.
ALTER TABLE vehicle_tracking.trip_notifications
    DROP CONSTRAINT trip_notifications_simulation_incident_id_key;
ALTER TABLE vehicle_tracking.trip_notifications
    ADD CONSTRAINT uq_trip_notification_incident_type UNIQUE (simulation_incident_id, type);

ALTER TABLE vehicle_tracking.trip_notifications DROP CONSTRAINT chk_trip_notification_type;
ALTER TABLE vehicle_tracking.trip_notifications ADD CONSTRAINT chk_trip_notification_type
    CHECK (type IN (
        'REROUTE_CREATED', 'REROUTE_UNAVAILABLE', 'OFF_ROUTE_DETECTED', 'DRIVER_ROUTE_CHANGED',
        'DISPATCH_ATTENTION', 'DRIVER_UNAVAILABLE', 'DISPATCH_REASSIGNED', 'TRIP_AUTO_STARTED',
        'DIRECT_ASSIGNMENT_ACCEPTED', 'DIRECT_ASSIGNMENT_DECLINED', 'SIMULATION_INCIDENT',
        'SIMULATION_INCIDENT_RESOLVED'
    ));
