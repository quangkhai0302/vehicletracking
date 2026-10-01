export type NotificationType =
  | 'REROUTE_CREATED' | 'REROUTE_UNAVAILABLE' | 'OFF_ROUTE_DETECTED' | 'DRIVER_ROUTE_CHANGED'
  | 'DISPATCH_ATTENTION' | 'DRIVER_UNAVAILABLE' | 'DISPATCH_REASSIGNED' | 'TRIP_AUTO_STARTED'
  | 'DIRECT_ASSIGNMENT_DECLINED';
export type NotificationSeverity = 'CRITICAL' | 'MAJOR';
export interface NotificationItem {
  id: number; tripId: number; vehicleId: number; vehiclePlateNumber: string; revisionId: number | null; type: NotificationType; severity: NotificationSeverity;
  title: string; reason: string; incidentId: string | null; affectedStopSequences: string;
  baselineEtaSeconds: number | null; revisedEtaSeconds: number | null; createdAt: string; readAt: string | null;
  measuredDistanceMeters?: number | null; thresholdDistanceMeters?: number | null; breachDurationSeconds?: number | null;
}
export interface RouteRevisionStop {
  originalStopSequence: number; sequenceNumber: number; stationId: number; stationName: string;
  latitude: number; longitude: number; dwellDurationSeconds: number;
  baselineArrivalAt: string; baselineDepartureAt: string; revisedArrivalAt: string; revisedDepartureAt: string;
}
export interface RouteRevisionSection {
  sectionSequence: number; destinationStopSequence: number; encodedPolyline: string;
  distanceMeters: number; travelDurationSeconds: number; baseTravelDurationSeconds: number;
}
export interface RouteRevision {
  id: number; tripId: number; sourceRouteId: number; revisionNumber: number; status: 'ACTIVE' | 'SUPERSEDED';
  reasonCode: 'ROAD_CLOSURE' | 'TRAFFIC_DELAY' | 'DRIVER_CHOICE'; reasonDetail: string | null; triggerIncidentId: string | null;
  severity: NotificationSeverity; baselineRemainingSeconds: number; revisedRemainingSeconds: number;
  createdAt: string; activatedAt: string; supersededAt: string | null;
  stops: RouteRevisionStop[]; sections: RouteRevisionSection[];
}
