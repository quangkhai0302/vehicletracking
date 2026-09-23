import type { OperationalReport, OperationalReportFilters } from '../types/reports';
import { appFetch } from '@/shared/api/http';

const BASE = `${(import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')}/api/v1`;

export async function fetchOperationalReport(filters: OperationalReportFilters, signal?: AbortSignal): Promise<OperationalReport> {
  const query = new URLSearchParams({ from: filters.from, to: filters.to });
  if (filters.vehicleId) query.set('vehicleId', String(filters.vehicleId));
  if (filters.driverId) query.set('driverId', String(filters.driverId));
  const response = await appFetch(`${BASE}/reports/operations?${query.toString()}`, { signal });
  if (!response.ok) {
    let message = `Không thể tải báo cáo (HTTP ${response.status}).`;
    try {
      const problem = await response.json() as { detail?: string; title?: string };
      message = problem.detail || problem.title || message;
    } catch { /* Keep the HTTP fallback when the response is not JSON. */ }
    throw new Error(message);
  }
  return response.json() as Promise<OperationalReport>;
}
