import { appFetch } from '@/shared/api/http';
import type { NotificationSeverity } from '@/features/reports/types/notifications';
import type { SimulationRun } from '@/features/tracking/types/operations';

const BASE = `${(import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')}/api/v1`;

export type SimulationIncidentType =
  | 'VEHICLE_BREAKDOWN'
  | 'EMERGENCY_STOP'
  | 'ROAD_BLOCKED'
  | 'OTHER';
export type SimulationIncidentStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED';

export interface SimulationIncidentResponse {
  id: number;
  tripId: number;
  vehicleId: number;
  vehiclePlateNumber: string;
  reportedByDriverId: number | null;
  reportedByDriverName: string | null;
  attemptNumber: number;
  type: SimulationIncidentType;
  severity: NotificationSeverity;
  status: SimulationIncidentStatus;
  detail: string | null;
  latitude: number;
  longitude: number;
  simulatedElapsedSeconds: number;
  createdAt: string;
  acknowledgedAt: string | null;
  resolvedAt: string | null;
  simulation: SimulationRun | null;
}

export async function reportDriverSimulationIncident(
  tripId: number,
  input: {
    attemptNumber: number;
    type: SimulationIncidentType;
    severity: NotificationSeverity;
    detail: string;
    idempotencyKey: string;
  },
  signal?: AbortSignal,
): Promise<SimulationIncidentResponse> {
  const response = await appFetch(`${BASE}/driver/trips/${tripId}/simulation/incidents`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
    signal,
  });
  if (!response.ok) throw new Error(`Không thể ghi nhận sự cố (HTTP ${response.status}).`);
  return response.json() as Promise<SimulationIncidentResponse>;
}

async function updateIncident(
  id: number,
  action: 'acknowledge' | 'resolve',
): Promise<SimulationIncidentResponse> {
  const response = await appFetch(`${BASE}/simulation-incidents/${id}/${action}`, { method: 'POST' });
  if (!response.ok) throw new Error(`Không thể cập nhật sự cố (HTTP ${response.status}).`);
  return response.json() as Promise<SimulationIncidentResponse>;
}

export const acknowledgeSimulationIncident = (id: number) => updateIncident(id, 'acknowledge');
export const resolveSimulationIncident = (id: number) => updateIncident(id, 'resolve');
