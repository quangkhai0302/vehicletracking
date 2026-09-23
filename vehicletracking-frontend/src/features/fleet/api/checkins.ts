import type { TripCheckIns } from '@/features/fleet/types/checkin';
import { appFetch } from '@/shared/api/http';

const BASE_URL = `${(import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')}/api/v1`;
export async function fetchTripCheckIns(tripId: number, signal?: AbortSignal): Promise<TripCheckIns> {
  const response = await appFetch(`${BASE_URL}/trips/${tripId}/check-ins`, { signal });
  if (!response.ok) {
    let detail = `Không thể tải dữ liệu check-in (HTTP ${response.status}).`;
    try { const problem = await response.json() as { detail?: string }; detail = problem.detail || detail; } catch { /* Keep fallback. */ }
    throw new Error(detail);
  }
  return response.json() as Promise<TripCheckIns>;
}
