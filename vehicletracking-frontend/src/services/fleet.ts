import type { FleetVehicle, VehicleInput, TripSummary, TripDetail, TripInput, TripAction, TripUpdateInput } from '../types/fleet';

const BASE_URL = `${(import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')}/api/v1`;

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.body) headers.set('Content-Type', 'application/json');
  const response = await fetch(BASE_URL + path, { ...options, headers });
  if (!response.ok) {
    let detail = `Không thể thực hiện yêu cầu (HTTP ${response.status}).`;
    try {
      const problem = await response.json() as { detail?: string; title?: string };
      detail = problem.detail || problem.title || detail;
    } catch { /* Keep HTTP error when the server response is not JSON. */ }
    throw new Error(detail);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}
export const fetchFleetVehicles = (signal?: AbortSignal) => request<FleetVehicle[]>('/vehicles', { signal });
export const createVehicle = (input: VehicleInput) => request<FleetVehicle>('/vehicles', { method: 'POST', body: JSON.stringify(input) });
export const updateVehicle = (id: number, input: VehicleInput) => request<FleetVehicle>(`/vehicles/${id}`, { method: 'PUT', body: JSON.stringify(input) });
export const deactivateVehicle = (id: number) => request<void>(`/vehicles/${id}`, { method: 'DELETE' });
export const fetchTrips = (signal?: AbortSignal) => request<TripSummary[]>('/trips', { signal });
export const fetchTrip = (id: number, signal?: AbortSignal) => request<TripDetail>(`/trips/${id}`, { signal });
export const createTrip = (input: TripInput) => request<TripDetail>('/trips', { method: 'POST', body: JSON.stringify(input) });
export const updateTrip = (id: number, input: TripUpdateInput) => request<TripDetail>(`/trips/${id}`, { method: 'PUT', body: JSON.stringify(input) });
export const deleteTrip = (id: number) => request<void>(`/trips/${id}`, { method: 'DELETE' });
export const changeTripStatus = (id: number, action: TripAction) => request<TripDetail>(`/trips/${id}/${action}`, { method: 'POST' });
