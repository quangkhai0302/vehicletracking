import type { Station, StationInput } from '../types/station';

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const STATIONS_URL = `${API_BASE_URL}/api/v1/stations`;

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
    let message = `Station API error: HTTP ${response.status}`;
    try {
      const problem = (await response.json()) as { detail?: string; title?: string };
      message = problem.detail || problem.title || message;
    } catch {
      // Response is not JSON; retain the safe status-based message.
    }
    throw new Error(message);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

export function fetchStations(): Promise<Station[]> {
  return request<Station[]>(STATIONS_URL);
}

export function createStation(input: StationInput): Promise<Station> {
  return request<Station>(STATIONS_URL, {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function updateStation(id: number, input: StationInput): Promise<Station> {
  return request<Station>(`${STATIONS_URL}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  });
}

export function deleteStation(id: number): Promise<void> {
  return request<void>(`${STATIONS_URL}/${id}`, { method: 'DELETE' });
}
