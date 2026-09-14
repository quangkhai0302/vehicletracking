import type { TelemetryPage } from '../types/telemetry';
import type { TelemetryPosition } from '../types/operations';
const BASE = `${(import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')}/api/v1`;
export interface TelemetryHistoryQuery { tripId?: number; vehicleId?: number; source?: TelemetryPosition['source']; from?: string; to?: string; page?: number; size?: number; }
export async function fetchTelemetryHistory(query: TelemetryHistoryQuery = {}, signal?: AbortSignal): Promise<TelemetryPage> {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => { if (value !== undefined) params.set(key, String(value)); });
  const response = await fetch(`${BASE}/telemetry/history?${params}`, { signal });
  if (!response.ok) throw new Error(`Không thể tải lịch sử vị trí (HTTP ${response.status}).`);
  return response.json() as Promise<TelemetryPage>;
}
