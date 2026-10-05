import type { AdminRegistrationInput, AuthUser, ChangePasswordInput, LoginInput, RegisteredAdmin } from '../types/auth';
import { appFetch } from '@/shared/api/http';

const BASE = `${(import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')}/api/v1`;

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.body) headers.set('Content-Type', 'application/json');
  const response = await appFetch(`${BASE}${path}`, { ...options, headers });
  if (!response.ok) {
    let message = `Không thể thực hiện yêu cầu (HTTP ${response.status}).`;
    try {
      const problem = await response.json() as { detail?: string; title?: string };
      message = problem.detail || problem.title || message;
    } catch { /* Keep the status fallback. */ }
    const error = new Error(message);
    (error as Error & { status?: number }).status = response.status;
    throw error;
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export const login = (input: LoginInput) => request<AuthUser>('/auth/login', { method: 'POST', body: JSON.stringify(input) });
export const registerAdmin = (input: AdminRegistrationInput) => request<RegisteredAdmin>('/auth/register-admin', { method: 'POST', body: JSON.stringify(input) });
export const fetchCurrentUser = () => request<AuthUser>('/auth/me');
export const logout = () => request<void>('/auth/logout', { method: 'POST' });
export const changePassword = (input: ChangePasswordInput) => request<void>('/auth/change-password', { method: 'POST', body: JSON.stringify(input) });
