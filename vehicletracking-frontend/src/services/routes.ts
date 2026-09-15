import type { RouteCreateInput, RouteDetail, RouteSummary, RouteShapePoint, RoutingProviderName } from '../types/route';

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const ROUTES_URL = `${API_BASE_URL}/api/v1/routes`;

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const headers = new Headers(options?.headers);
  if (options?.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  const response = await fetch(url, {
    ...options,
    headers,
  });

  if (!response.ok) {
    let message = `Route API error: HTTP ${response.status}`;
    try {
      const problem = (await response.json()) as { detail?: string; title?: string; code?: string };
      message = problem.detail || problem.title || message;
    } catch {
      // Response is not JSON; keep fallback status-based message.
    }
    throw new Error(message);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export function fetchRoutes(signal?: AbortSignal): Promise<RouteSummary[]> {
  return request<RouteSummary[]>(ROUTES_URL, { signal });
}

export function fetchRouteById(id: number, signal?: AbortSignal): Promise<RouteDetail> {
  return request<RouteDetail>(`${ROUTES_URL}/${id}`, { signal });
}

export function createRoute(input: RouteCreateInput): Promise<RouteDetail> {
  return request<RouteDetail>(ROUTES_URL, {
    method: 'POST',
    body: JSON.stringify(input),
  });
}
export function updateRoute(id: number, input: RouteCreateInput): Promise<RouteDetail> {
  return request<RouteDetail>(`${ROUTES_URL}/${id}`, { method: 'PUT', body: JSON.stringify(input) });
}
export function deactivateRoute(id: number): Promise<void> {
  return request<void>(`${ROUTES_URL}/${id}`, { method: 'DELETE' }).then(() => undefined);
}
export function shapeRoute(id: number, points: RouteShapePoint[], action: 'preview' | 'save' | 'copy', signal?: AbortSignal,
  targetProvider?: RoutingProviderName) {
  return request<RouteDetail>(`${ROUTES_URL}/${id}/shape${action === 'save' ? '' : `/${action}`}`, {
    method: action === 'save' ? 'PUT' : 'POST', body: JSON.stringify({points, targetProvider}), signal,
  });
}
