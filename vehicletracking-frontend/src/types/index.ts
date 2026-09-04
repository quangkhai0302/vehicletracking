export type StationType = 'START' | 'STOP' | 'END';
export type VehicleStatus = 'IDLE' | 'IN_TRANSIT' | 'MAINTENANCE';
export type TripStatus = 'SCHEDULED' | 'RUNNING' | 'COMPLETED' | 'DELAYED' | 'CANCELLED';
export type CheckInStatus = 'PENDING' | 'CHECKED_IN' | 'SKIPPED';
export type IncidentType = 'CONGESTION' | 'ACCIDENT' | 'CONSTRUCTION' | 'BAD_WEATHER';

export interface Station {
  id: number;
  code: string;
  name: string;
  latitude: number;
  longitude: number;
  address?: string;
  radiusMeters: number;
  stationType: StationType;
  createdAt?: string;
}

export interface RouteStation {
  id: number;
  stopOrder: number;
  station: Station;
  distanceToNextKm: number;
  estimatedTimeToNextMinutes: number;
}

export interface Route {
  id: number;
  code: string;
  name: string;
  description?: string;
  totalDistanceKm: number;
  estimatedDurationMinutes: number;
  stations: RouteStation[];
  createdAt?: string;
}

export interface RouteRequest {
  code?: string;
  name: string;
  description?: string;
  stationIds: number[];
}

export interface Vehicle {
  id: number;
  plateNumber: string;
  model: string;
  status: VehicleStatus;
  currentLatitude?: number;
  currentLongitude?: number;
  currentSpeed?: number;
  currentHeading?: number;
  lastUpdatedAt?: string;
}

export interface TripCheckIn {
  id: number;
  stationId: number;
  stationName: string;
  stationCode: string;
  latitude: number;
  longitude: number;
  stopOrder: number;
  scheduledArrivalTime: string;
  actualArrivalTime?: string;
  status: CheckInStatus;
}

export interface Trip {
  id: number;
  tripCode: string;
  routeId: number;
  routeName: string;
  vehicleId: number;
  vehiclePlateNumber: string;
  startTime?: string;
  endTime?: string;
  status: TripStatus;
  checkIns: TripCheckIn[];
  createdAt?: string;
}

export interface StationEta {
  stationId: number;
  stationName: string;
  stationCode: string;
  stopOrder: number;
  distanceRemainingMeters: number;
  etaSeconds: number;
  estimatedArrivalTime: string;
  status: CheckInStatus;
}

export interface SimulatorResponse {
  message?: string;
  status: 'IDLE' | 'RUNNING' | 'PAUSED' | 'COMPLETED' | string;
  tripId?: number;
  simulationRunId?: string;
  multiplier?: number;
  currentWaypointIndex?: number;
  lastPublishedSequence?: number;
}

export interface VehicleTelemetry {
  simulationRunId?: string;
  sequence?: number;
  vehicleId: number;
  plateNumber: string;
  tripId: number;
  tripCode: string;
  tripStatus?: TripStatus;
  routeId: number;
  routeName: string;
  latitude: number;
  longitude: number;
  speed: number;
  heading: number;
  status: VehicleStatus;
  currentStopIndex: number;
  targetStationId?: number;
  targetStationName?: string;
  distanceToTargetMeters: number;
  etaSecondsToTarget: number;
  etaSecondsToCompletion?: number;
  estimatedCompletionTime?: string;
  stationsEta: StationEta[];
  inIncidentZone: boolean;
  currentIncidentNotice?: string;
  timestamp: string;
}

export interface TrafficIncident {
  id: number;
  title: string;
  type: IncidentType;
  latitude: number;
  longitude: number;
  radiusMeters: number;
  speedReductionPercent: number;
  description?: string;
  active: boolean;
  createdAt?: string;
}

export interface CheckInEvent {
  tripId: number;
  tripCode: string;
  vehicleId: number;
  plateNumber: string;
  stationId: number;
  stationName: string;
  stopOrder: number;
  checkInTime: string;
  message: string;
}

export interface AlertMessage {
  id: string;
  level: 'INFO' | 'WARNING' | 'DANGER';
  title: string;
  message: string;
  tripId?: number;
  vehicleId?: number;
  incidentId?: number;
  timestamp: string;
}

export interface ToastItem {
  id: string;
  type: 'CHECK_IN' | 'DELAY_ALERT' | 'INFO';
  level: 'INFO' | 'WARNING' | 'DANGER';
  title: string;
  message: string;
  time?: string;
}
