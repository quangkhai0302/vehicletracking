import type { TripEta } from '../types/eta';

const BASE_URL = `${(import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')}/api/v1`;

export async function fetchTripEta(tripId: number, signal?: AbortSignal): Promise<TripEta> {
  const response = await fetch(`${BASE_URL}/trips/${tripId}/eta`, { signal });
  if (!response.ok) {
    let detail = `Không thể tải ETA theo traffic (HTTP ${response.status}).`;
    try {
      const problem = await response.json() as { detail?: string };
      detail = problem.detail || detail;
    } catch { /* Keep HTTP fallback. */ }
    throw new Error(detail);
  }
  return response.json() as Promise<TripEta>;
}
