import { appFetch } from '@/shared/api/http';
import type {
  DispatchDetail, DispatchOffer, DriverDispatchDetail, DriverDispatchInboxItem,
} from '../types/dispatch';

const BASE = `${(import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')}/api/v1`;

export class DispatchApiError extends Error {
  constructor(public readonly status: number, public readonly code: string | null, detail: string) {
    super(detail);
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.body) headers.set('Content-Type', 'application/json');
  const response = await appFetch(`${BASE}${path}`, { ...options, headers });
  if (!response.ok) {
    let detail = `Không thể thực hiện yêu cầu (HTTP ${response.status}).`;
    let code: string | null = null;
    try {
      const problem = await response.json() as { detail?: string; title?: string; code?: string };
      detail = problem.detail || problem.title || detail;
      code = problem.code ?? null;
    } catch { /* Keep fallback for non-JSON errors. */ }
    throw new DispatchApiError(response.status, code, detail);
  }
  return response.json() as Promise<T>;
}

export const fetchDispatchDetail = (tripId: number, signal?: AbortSignal) =>
  request<DispatchDetail>(`/trips/${tripId}/dispatch`, { signal });
export const updateTripDispatchPolicy = (tripId: number, input: {
  expectedRevision: number | null; startMode: 'MANUAL' | 'AUTO_IF_READY';
  backupEnabled: boolean; backupDriverIds: number[];
}) => request<DispatchDetail>(`/trips/${tripId}/dispatch/policy`,
  { method: 'PUT', body: JSON.stringify(input) });
export const overrideTripStart = (tripId: number, expectedRevision: number, reason: string) =>
  request<{ dispatch: DispatchDetail; simulation: unknown }>(`/trips/${tripId}/dispatch/override-start`,
    { method: 'POST', body: JSON.stringify({ expectedRevision, reason }) });

export const fetchDriverDispatch = (tripId: number, signal?: AbortSignal) =>
  request<DriverDispatchDetail>(`/driver/trips/${tripId}/dispatch`, { signal });
export const readyForTrip = (tripId: number, expectedRevision: number) =>
  request<DriverDispatchDetail>(`/driver/trips/${tripId}/dispatch/ready`,
    { method: 'POST', body: JSON.stringify({ expectedRevision }) });
export const reportUnavailable = (tripId: number, expectedRevision: number, reason: string) =>
  request<{ tripId: number; state: string; revision: number }>(`/driver/trips/${tripId}/dispatch/unavailable`,
    { method: 'POST', body: JSON.stringify({ expectedRevision, reason }) });
export const fetchDispatchOffers = (signal?: AbortSignal) =>
  request<DispatchOffer[]>('/driver/dispatch/offers', { signal });
export const acceptDispatchOffer = (offerId: string, expectedRevision: number) =>
  request<{ tripId: number; status: 'ACCEPTED'; dispatch: DriverDispatchDetail }>(
    `/driver/dispatch/offers/${encodeURIComponent(offerId)}/accept`,
    { method: 'POST', body: JSON.stringify({ expectedRevision }) });
export const declineDispatchOffer = (offerId: string, expectedRevision: number, reason?: string) =>
  request<{ tripId: number; status: 'DECLINED' }>(
    `/driver/dispatch/offers/${encodeURIComponent(offerId)}/decline`,
    { method: 'POST', body: JSON.stringify({ expectedRevision, reason: reason || null }) });
export const fetchDispatchInbox = (signal?: AbortSignal) =>
  request<DriverDispatchInboxItem[]>('/driver/dispatch/inbox?limit=50', { signal });
export const readDispatchInboxItem = (id: number) =>
  request<DriverDispatchInboxItem>(`/driver/dispatch/inbox/${id}/read`, { method: 'POST' });
