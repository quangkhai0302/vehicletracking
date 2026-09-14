import type { TripSummary } from './fleet';
import type { TripCheckIns } from './checkin';
import type { TrafficSource, TrafficStatus } from './traffic';
import type { NotificationItem } from './notifications';

export interface TelemetryPosition {
  id: number; eventId: string; vehicleId: number; tripId: number;
  recordedAt: string; receivedAt: string; simulatedAt: string | null;
  latitude: number; longitude: number; speedKmh: number; heading: number; accuracyMeters: number;
  source: 'GPS' | 'SIMULATOR';
}
export type SimulationStatus = 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'STOPPED' | 'FAILED';
export interface SimulationFrame {
  latitude: number; longitude: number; heading: number; speedKmh: number; progressPercent: number;
  nextStopSequence: number; nextStopEtaSeconds: number; dwelling: boolean; finished: boolean;
}
export interface SimulationRun {
  id: number; tripId: number; status: SimulationStatus; multiplier: 1 | 5 | 10;
  elapsedSeconds: number; durationSeconds: number; simulatedAt: string; updatedAt: string;
  errorMessage: string | null; replacementTripId: number | null; frame: SimulationFrame | null;
  traffic?: { source: TrafficSource; status: TrafficStatus; nextStopEtaSeconds: number | null;
    observedAt: string | null; fetchedAt: string | null; blocked: boolean; warning: string | null } | null;
}
export interface OperationsSnapshot {
  serverTime: string; positions: TelemetryPosition[]; simulations: SimulationRun[]; trips: TripSummary[]; checkIns: TripCheckIns[]; notifications: NotificationItem[];
}
export type StreamConnection = 'connecting' | 'live' | 'reconnecting';
export type SimulationAction = 'play' | 'pause' | 'speed' | 'stop' | 'reset';
export const SIMULATION_LABELS: Record<SimulationStatus, string> = {
  RUNNING: 'Đang mô phỏng', PAUSED: 'Đã tạm dừng', COMPLETED: 'Đã hoàn thành', STOPPED: 'Đã dừng', FAILED: 'Mô phỏng gặp lỗi',
};
export function positionFreshness(position: TelemetryPosition, now: number): 'fresh' | 'stale' | 'offline' {
  const age = Math.max(0, now - Date.parse(position.recordedAt));
  return age > 60_000 ? 'offline' : age > 15_000 ? 'stale' : 'fresh';
}
