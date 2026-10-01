import type {
  Driver,
  DriverInput,
  FleetVehicle,
  VehicleInput,
  TripSummary,
  TripDetail,
  TripInput,
  TripAction,
} from '@/features/fleet/types/fleet';
import type { RouteDetail } from '@/features/routes/types/route';
import { appFetch } from '@/shared/api/http';

const BASE_URL = `${(import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')}/api/v1`;

export class FleetApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string | null,
    detail: string,
  ) {
    super(detail);
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.body) headers.set('Content-Type', 'application/json');
  const response = await appFetch(BASE_URL + path, { ...options, headers });
  if (!response.ok) {
    let detail = `Không thể thực hiện yêu cầu (HTTP ${response.status}).`;
    let code: string | null = null;
    try {
      const problem = (await response.json()) as { detail?: string; title?: string; code?: string };
      detail = problem.detail || problem.title || detail;
      code = problem.code ?? null;
    } catch {
      /* Keep HTTP error when the server response is not JSON. */
    }
    throw new FleetApiError(response.status, code, detail);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}
export const fetchFleetVehicles = (signal?: AbortSignal) =>
  request<FleetVehicle[]>('/vehicles', { signal });
const vehicleDetails = (input: VehicleInput) => ({
  plateNumber: input.plateNumber,
  name: input.name,
  description: input.description,
  vehicleType: input.vehicleType,
});
export const createVehicle = (input: VehicleInput) =>
  request<FleetVehicle>('/vehicles', {
    method: 'POST',
    body: JSON.stringify(vehicleDetails(input)),
  });
export const updateVehicle = (id: number, input: VehicleInput) =>
  request<FleetVehicle>(`/vehicles/${id}`, {
    method: 'PUT',
    body: JSON.stringify(vehicleDetails(input)),
  });
export const assignVehicleDriver = (id: number, driverId: number) =>
  request<FleetVehicle>(`/vehicles/${id}/driver`, {
    method: 'PUT',
    body: JSON.stringify({ driverId }),
  });
export const unassignVehicleDriver = (id: number) =>
  request<void>(`/vehicles/${id}/driver`, { method: 'DELETE' });
export const deactivateVehicle = (id: number) =>
  request<void>(`/vehicles/${id}`, { method: 'DELETE' });
export const fetchDrivers = (signal?: AbortSignal) => request<Driver[]>('/drivers', { signal });
export const createDriver = (input: DriverInput) =>
  request<Driver>('/drivers', { method: 'POST', body: JSON.stringify(input) });
export const updateDriver = (id: number, input: DriverInput) =>
  request<Driver>(`/drivers/${id}`, { method: 'PUT', body: JSON.stringify(input) });
export const deactivateDriver = (id: number) =>
  request<void>(`/drivers/${id}`, { method: 'DELETE' });
export const fetchTrips = (signal?: AbortSignal) => request<TripSummary[]>('/trips', { signal });
export const fetchTrip = (id: number, signal?: AbortSignal) =>
  request<TripDetail>(`/trips/${id}`, { signal });
export const fetchTripRoute = (id: number, signal?: AbortSignal) =>
  request<RouteDetail>(`/trips/${id}/route`, { signal });
export const createTrip = (input: TripInput) =>
  request<TripDetail>('/trips', { method: 'POST', body: JSON.stringify(input) });
export const deleteTrip = (id: number) => request<void>(`/trips/${id}`, { method: 'DELETE' });
export const changeTripStatus = (id: number, action: TripAction, reason?: string) =>
  request<TripDetail>(`/trips/${id}/${action}`, {
    method: 'POST',
    body: action === 'cancel' ? JSON.stringify({ reason }) : undefined,
  });
export const assignTripDriver = (id: number, driverId: number) =>
  request<TripDetail>(`/trips/${id}/driver`, { method: 'PUT', body: JSON.stringify({ driverId }) });
export const unassignTripDriver = (id: number) =>
  request<void>(`/trips/${id}/driver`, { method: 'DELETE' });
export const assignTripVehicle = (id: number, vehicleId: number) =>
  request<TripDetail>(`/trips/${id}/vehicle`, {
    method: 'PUT',
    body: JSON.stringify({ vehicleId }),
  });
