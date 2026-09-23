import type { TripSchedule, TripScheduleInput } from '../types/schedule';
import { appFetch } from '@/shared/api/http';

const BASE_URL = `${(import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')}/api/v1`;

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.body) headers.set('Content-Type', 'application/json');
  const response = await appFetch(`${BASE_URL}${path}`, { ...options, headers });
  if (!response.ok) {
    let detail = `Không thể thực hiện yêu cầu (HTTP ${response.status}).`;
    try {
      const problem = await response.json() as { detail?: string; title?: string };
      detail = problem.detail || problem.title || detail;
    } catch { /* Keep the HTTP status message for non-problem responses. */ }
    throw new Error(detail);
  }
  return response.json() as Promise<T>;
}

export const fetchSchedules = (signal?: AbortSignal) => request<TripSchedule[]>('/schedules', { signal });
export const createSchedule = (input: TripScheduleInput) => request<TripSchedule>('/schedules', { method: 'POST', body: JSON.stringify(input) });
export const updateSchedule = (id: number, input: TripScheduleInput) => request<TripSchedule>(`/schedules/${id}`, { method: 'PUT', body: JSON.stringify(input) });
export const setScheduleEnabled = (id: number, enabled: boolean) => request<TripSchedule>(`/schedules/${id}/${enabled ? 'enable' : 'disable'}`, { method: 'POST' });
