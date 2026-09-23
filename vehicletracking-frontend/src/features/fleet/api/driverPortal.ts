import type { TripDetail, TripStatus, TripSummary } from '@/features/fleet/types/fleet';
import type { TripSchedule } from '@/features/schedules/types/schedule';
import { appFetch } from '@/shared/api/http';

const BASE = `${(import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')}/api/v1`;

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await appFetch(`${BASE}${path}`, options);
  if (!response.ok) {
    let message = `Không thể tải dữ liệu được phân công (HTTP ${response.status}).`;
    try { const problem = await response.json() as { detail?: string; title?: string }; message = problem.detail || problem.title || message; } catch { /* Keep status fallback. */ }
    throw new Error(message);
  }
  return response.json() as Promise<T>;
}

export function fetchMyTrips(options: { status?: TripStatus; from?: string; to?: string } = {}, signal?: AbortSignal) {
  const query = new URLSearchParams();
  if (options.status) query.set('status', options.status);
  if (options.from) query.set('from', options.from);
  if (options.to) query.set('to', options.to);
  const queryString = query.toString();
  return request<TripSummary[]>(`/driver/trips${queryString ? `?${queryString}` : ''}`, { signal });
}

export const fetchMyTrip = (id: number, signal?: AbortSignal) => request<TripDetail>(`/driver/trips/${id}`, { signal });
export const fetchMySchedules = (signal?: AbortSignal) => request<TripSchedule[]>('/driver/schedules', { signal });
