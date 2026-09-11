export type VehicleStatus = 'RUNNING' | 'AT_STATION' | 'DELAYED' | 'OFFLINE';

export interface VehicleTimelineStop {
  stationId: string;
  stationName: string;
  plannedTime: string;
  actualOrEtaTime: string;
  status: 'PASSED' | 'CURRENT' | 'UPCOMING';
}

export interface Vehicle {
  id: string;
  plateNumber: string;
  model: string;
  routeId: string;
  routeName: string;
  driverName: string;
  driverPhone: string;
  speedKmh: number;
  desiredSpeedKmh: number;
  speedLimitKmh: number;
  trafficSpeedKmh: number;
  status: VehicleStatus;
  latitude: number;
  longitude: number;
  heading: number;
  nextStationName: string;
  etaMinutes: number;
  distanceToNextMeters: number;
  tripProgressPercent: number;
  timeline: VehicleTimelineStop[];
}

export type SimulatorMultiplier = 1 | 2 | 5 | 10;

export interface SimulatorConfig {
  isRunning: boolean;
  multiplier: SimulatorMultiplier;
}
