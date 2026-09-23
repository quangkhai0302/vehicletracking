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
