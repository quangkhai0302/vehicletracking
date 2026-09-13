export type RouteTransportMode = 'CAR';
export type RoutingProviderName = 'HERE';
export type RouteStopRole = 'START' | 'STOP' | 'END';

export interface RouteStopInput {
  stationId: number;
  dwellDurationSeconds: number;
}

export interface RouteCreateInput {
  name: string;
  stops: RouteStopInput[];
}

export interface RouteStop {
  sequenceNumber: number;
  role: RouteStopRole;
  stationId: number;
  stationName: string;
  latitude: number;
  longitude: number;
  dwellDurationSeconds: number;
  distanceFromPreviousMeters: number;
  travelDurationFromPreviousSeconds: number;
  arrivalOffsetSeconds: number;
  departureOffsetSeconds: number;
}

export interface RouteSection {
  sectionSequence: number;
  destinationStopSequence: number;
  encodedPolyline: string;
  distanceMeters: number;
  travelDurationSeconds: number;
  baseTravelDurationSeconds: number;
}

export interface RouteSummary {
  id: number;
  name: string;
  transportMode: RouteTransportMode;
  routingProvider: RoutingProviderName;
  startStationName: string;
  endStationName: string;
  stopCount: number;
  totalDistanceMeters: number;
  estimatedTravelDurationSeconds: number;
  totalDwellDurationSeconds: number;
  estimatedTripDurationSeconds: number;
  calculatedAt: string;
  createdAt: string;
}

export interface RouteDetail {
  id: number;
  name: string;
  transportMode: RouteTransportMode;
  routingProvider: RoutingProviderName;
  totalDistanceMeters: number;
  estimatedTravelDurationSeconds: number;
  baseTravelDurationSeconds: number;
  totalDwellDurationSeconds: number;
  estimatedTripDurationSeconds: number;
  estimatedDepartureAt: string;
  calculatedAt: string;
  createdAt: string;
  stops: RouteStop[];
  sections: RouteSection[];
}

