import type { DashboardSummary } from '../types/dashboard';
import { appFetch } from '@/shared/api/http';

const BASE = `${(import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')}/api/v1`;

export async function fetchDashboardSummary(signal?: AbortSignal): Promise<DashboardSummary> {
  const response = await appFetch(`${BASE}/dashboard/summary`, { signal });
  if (!response.ok) {
    let message = `Không thể tải tổng quan vận hành (HTTP ${response.status}).`;
    try {
      const problem = await response.json() as { detail?: string; title?: string };
      message = problem.detail || problem.title || message;
    } catch { /* Keep HTTP fallback. */ }
    throw new Error(message);
  }
  return response.json() as Promise<DashboardSummary>;
}
