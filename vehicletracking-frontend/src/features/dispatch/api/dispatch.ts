import { appFetch } from '@/shared/api/http';
import type {
  DriverDispatchInboxItem,
  AssignmentActionResponse, DriverAssignmentRequest,
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

export const fetchDriverAssignmentRequests = (signal?: AbortSignal) =>
  request<DriverAssignmentRequest[]>('/driver/assignment-requests', { signal });
export const acceptDriverAssignmentRequest = (requestId: string) =>
  request<AssignmentActionResponse>(
    `/driver/assignment-requests/${encodeURIComponent(requestId)}/accept`,
    { method: 'POST' },
  );
export const declineDriverAssignmentRequest = (requestId: string, reason: string) =>
  request<AssignmentActionResponse>(
    `/driver/assignment-requests/${encodeURIComponent(requestId)}/decline`,
    { method: 'POST', body: JSON.stringify({ reason }) },
  );
export const fetchDispatchInbox = (signal?: AbortSignal) =>
  request<DriverDispatchInboxItem[]>('/driver/dispatch/inbox?limit=50', { signal });
export const readDispatchInboxItem = (id: number) =>
  request<DriverDispatchInboxItem>(`/driver/dispatch/inbox/${id}/read`, { method: 'POST' });
