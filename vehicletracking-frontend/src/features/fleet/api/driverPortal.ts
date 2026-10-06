import type { TripDetail, TripStatus, TripSummary } from '@/features/fleet/types/fleet';
import type { TripSchedule } from '@/features/schedules/types/schedule';
import type { StopVisit } from '@/features/fleet/types/checkin';
import { appFetch } from '@/shared/api/http';
import type { DriverNavigationSnapshot, DriverRouteOptions } from '@/features/fleet/types/driverNavigation';

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

export const fetchDriverNavigation = (id: number, signal?: AbortSignal) =>
  request<DriverNavigationSnapshot>(`/driver/trips/${id}/navigation`, { signal });
export const startDriverTrip = (id: number, signal?: AbortSignal) =>
  request<DriverNavigationSnapshot>(`/driver/trips/${id}/start`, { method: 'POST', signal });
export const confirmDriverBoardingCount = (id: number, sequence: number, employeeBoardingCount: number, signal?: AbortSignal) =>
  request<StopVisit>(`/driver/trips/${id}/check-ins/${sequence}/boarding-count`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ employeeBoardingCount }), signal,
  });
export const fetchDriverRouteOptions = (id: number, signal?: AbortSignal) =>
  request<DriverRouteOptions>(`/driver/trips/${id}/route-options`, { method: 'POST', signal });
export const applyDriverRouteOption = (id: number, token: string, optionIndex: number, signal?: AbortSignal) =>
  request<DriverNavigationSnapshot>(`/driver/trips/${id}/route-options/${encodeURIComponent(token)}/apply`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ optionIndex }), signal,
  });
