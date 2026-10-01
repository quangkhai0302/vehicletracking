import { appFetch } from '@/shared/api/http';

const BASE = `${(import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')}/api/v1`;
export interface UserAccount {
  id: number;
  username: string;
  role: 'ADMIN' | 'DRIVER';
  active: boolean;
  driverId: number | null;
  driverName: string | null;
  driverLicenseNumber: string | null;
}
export interface DriverAccountInput {
  driverId: number;
}
export interface DriverAccountCreated extends UserAccount {
  temporaryPassword: string;
}
export interface DriverPasswordResetInput {
  password: string;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.body) headers.set('Content-Type', 'application/json');
  const response = await appFetch(`${BASE}${path}`, { ...options, headers });
  if (!response.ok) {
    let message = `Không thể thực hiện yêu cầu tài khoản (HTTP ${response.status}).`;
    try {
      const problem = (await response.json()) as { detail?: string; title?: string };
      message = problem.detail || problem.title || message;
    } catch {
      /* Keep fallback. */
    }
    throw new Error(message);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export const fetchUserAccounts = (signal?: AbortSignal) =>
  request<UserAccount[]>('/users', { signal });
export const createDriverAccount = (input: DriverAccountInput) =>
  request<DriverAccountCreated>('/users/driver', { method: 'POST', body: JSON.stringify(input) });
export const setUserAccountActive = (id: number, active: boolean) =>
  request<UserAccount>(`/users/${id}/${active ? 'enable' : 'disable'}`, { method: 'POST' });
export const resetDriverPassword = (id: number, input: DriverPasswordResetInput) =>
  request<void>(`/users/${id}/reset-password`, { method: 'POST', body: JSON.stringify(input) });
