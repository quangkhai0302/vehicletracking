import type { TripDetail, TripSummary } from './fleet';
import type { RouteDetail, RouteInstruction, RouteSection } from '@/features/routes/types/route';
import type { SimulationRun, TelemetryPosition } from '@/features/tracking/types/operations';
import type { TripCheckIns } from './checkin';
import type { Station } from '@/features/stations/types/station';

export interface DriverNavigationSnapshot {
  serverTime: string;
  trip: TripSummary;
  stops: TripDetail['stops'];
  route: RouteDetail;
  position: TelemetryPosition | null;
  simulation: SimulationRun | null;
  routeRevisionId: number | null;
  guidance: { maneuver: RouteInstruction; distanceMeters: number } | null;
  checkIns: TripCheckIns;
  stations: Station[];
}
export interface DriverRouteOption {
  optionIndex: number;
  label: string;
  distanceMeters: number;
  durationSeconds: number;
  sections: RouteSection[];
}
export interface DriverRouteOptions {
  token: string;
  expiresAt: string;
  routeRevisionId: number | null;
  options: DriverRouteOption[];
}
