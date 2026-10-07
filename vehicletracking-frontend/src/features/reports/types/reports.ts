import type { TripStatus } from '@/features/fleet/types/fleet';

export interface OperationalReportFilters {
  from: string;
  to: string;
  vehicleId?: number;
  driverId?: number;
}

export interface OperationalReport {
  from: string;
  to: string;
  generatedAt: string;
  tripCount: number;
  completedTripCount: number;
  totalDistanceMeters: number;
  totalRunningSeconds: number;
  onTimeRatePercent: number;
  lateTripCount: number;
  offRouteEventCount: number;
  overspeedEventCount: number;
  speedLimitKmh: number;
}

export interface OperationalReportVehicleRow {
  vehicleId: number | null;
  plateNumber: string | null;
  vehicleName: string | null;
  tripCount: number;
  completedTripCount: number;
  lateTripCount: number;
  lateStopCount: number;
  incidentCount: number;
  employeePassengerCount: number | null;
}

export interface OperationalReportDriverRow {
  driverId: number | null;
  driverName: string | null;
  tripCount: number;
  completedTripCount: number;
  lateTripCount: number;
  lateStopCount: number;
  incidentCount: number;
  employeePassengerCount: number | null;
  trips: OperationalReportDriverTrip[];
}

export interface OperationalReportDriverTrip {
  tripId: number;
  routeName: string | null;
  vehiclePlateNumber: string | null;
  scheduledDepartureAt: string;
  startedAt: string;
  endedAt: string | null;
  status: TripStatus;
}

export interface OperationalReportLateStop {
  tripId: number;
  routeName: string | null;
  vehiclePlateNumber: string | null;
  driverName: string | null;
  stationName: string;
  stopSequence: number;
  plannedArrivalAt: string;
  actualArrivalAt: string;
  delaySeconds: number;
}

export interface OperationalReportIncidentRow {
  type: string;
  severity: string;
  count: number;
}

export interface OperationalReportIncidentDetail {
  id: string;
  tripId: number;
  routeName: string | null;
  vehiclePlateNumber: string | null;
  driverName: string | null;
  type: string;
  severity: string;
  occurredAt: string;
  status: 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED' | 'RECORDED';
  detail: string | null;
}

export interface OperationalReportDetail {
  from: string;
  to: string;
  generatedAt: string;
  summary: OperationalReport;
  vehicles: OperationalReportVehicleRow[];
  drivers: OperationalReportDriverRow[];
  lateStops: OperationalReportLateStop[];
  incidents: OperationalReportIncidentRow[];
  incidentDetails: OperationalReportIncidentDetail[];
  employeePassengerDataAvailable: boolean;
  employeePassengerDataNote: string;
  employeeOccupancy: EmployeeOccupancySummary;
  employeeOccupancyByVehicle: EmployeeOccupancyVehicleRow[];
}

export interface EmployeeOccupancySummary {
  completedTripCount: number;
  tripsWithCompleteBoardingData: number;
  tripsMissingBoardingData: number;
  tripsMissingSeatCapacity: number;
  totalBoardings: number;
  averageBoardingsPerTrip: number | null;
  averageOnboard: number | null;
  seatUtilizationPercent: number | null;
}

export interface EmployeeOccupancyVehicleRow {
  vehicleId: number | null;
  plateNumber: string | null;
  vehicleName: string | null;
  seatCapacity: number | null;
  completedTripCount: number;
  tripsWithCompleteBoardingData: number;
  tripsMissingBoardingData: number;
  totalBoardings: number;
  averageBoardingsPerTrip: number | null;
  averageOnboard: number | null;
  seatUtilizationPercent: number | null;
}

export type SimulationReportMetric = 'ALL' | 'COMPLETED' | 'ON_TIME' | 'LATE' | 'OFF_ROUTE';
export interface SimulationReportFilters extends OperationalReportFilters {
  metric?: SimulationReportMetric;
  page?: number;
  size?: number;
}
export interface SimulationReportRevision {
  revisionId: number;
  revisionNumber: number;
  createdAt: string;
  baselineEtaSeconds: number | null;
  revisedEtaSeconds: number | null;
}
export interface SimulationReportItem {
  tripId: number;
  attemptNumber: number;
  current: boolean;
  vehicleId: number | null;
  vehiclePlateNumber: string | null;
  driverId: number | null;
  driverName: string | null;
  routeName: string | null;
  startedAt: string | null;
  endedAt: string | null;
  status: string;
  scenario: string | null;
  plannedDurationSeconds: number | null;
  plannedDistanceMeters: number | null;
  virtualElapsedSeconds: number | null;
  progressSeconds: number | null;
  latenessSeconds: number | null;
  punctuality: 'ON_TIME' | 'LATE' | 'IN_PROGRESS' | 'NOT_COMPLETED' | 'UNKNOWN';
  metadataComplete: boolean;
  offRouteEventCount: number;
  routeRevisions: SimulationReportRevision[];
}
export interface SimulationReport {
  from: string;
  to: string;
  generatedAt: string;
  attemptCount: number;
  completedAttemptCount: number;
  knownCompletedAttemptCount: number;
  totalPlannedDistanceMeters: number;
  totalVirtualSeconds: number;
  onTimeRatePercent: number | null;
  lateAttemptCount: number;
  offRouteEventCount: number;
  unknownAttemptCount: number;
  items: SimulationReportItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
