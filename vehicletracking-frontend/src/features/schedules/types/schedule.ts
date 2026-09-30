import type { DispatchStartMode } from '@/features/dispatch/types/dispatch';
export type ScheduleFrequency = 'ONCE' | 'WEEKLY';
export type ScheduleRunStatus = 'SUCCESS' | 'FAILED';

export interface TripSchedule {
  id: number;
  name: string | null;
  routeId: number;
  routeName: string;
  vehicleId: number;
  vehiclePlate: string;
  driverId: number;
  driverName: string;
  frequency: ScheduleFrequency;
  scheduledDate: string | null;
  weekdaysMask: number;
  departureTime: string;
  timezone: string;
  effectiveFrom: string;
  effectiveUntil: string | null;
  enabled: boolean;
  nextRunAt: string | null;
  lastRunAt: string | null;
  lastRunStatus: ScheduleRunStatus | null;
  lastRunMessage: string | null;
  startMode?: DispatchStartMode;
  backupEnabled?: boolean;
  backupDriverIds?: number[];
}

export interface TripScheduleInput {
  name: string | null;
  routeId: number;
  vehicleId: number;
  driverId: number;
  frequency: ScheduleFrequency;
  scheduledDate: string | null;
  weekdaysMask: number;
  departureTime: string;
  timezone: string;
  effectiveFrom: string;
  effectiveUntil: string | null;
  startMode?: DispatchStartMode;
  backupEnabled?: boolean;
  backupDriverIds?: number[];
}
