import type {
  OperationalReport,
  OperationalReportFilters,
  OperationalReportDetail,
  SimulationReport,
  SimulationReportFilters,
} from '../types/reports';
import { appFetch } from '@/shared/api/http';

const BASE = `${(import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')}/api/v1`;

export async function fetchSimulationReport(
  filters: SimulationReportFilters,
  signal?: AbortSignal,
): Promise<SimulationReport> {
  const query = new URLSearchParams({ from: filters.from, to: filters.to });
  if (filters.vehicleId) query.set('vehicleId', String(filters.vehicleId));
  if (filters.driverId) query.set('driverId', String(filters.driverId));
  query.set('metric', filters.metric ?? 'ALL');
  query.set('page', String(filters.page ?? 0));
  query.set('size', String(filters.size ?? 20));
  const response = await appFetch(`${BASE}/reports/simulation?${query}`, { signal });
  if (!response.ok) {
    let message = `Không thể tải báo cáo mô phỏng (HTTP ${response.status}).`;
    try {
      const problem = (await response.json()) as { detail?: string; title?: string };
      message = problem.detail || problem.title || message;
    } catch {
      /* Use the controlled HTTP fallback. */
    }
    throw new Error(message);
  }
  return response.json() as Promise<SimulationReport>;
}

export async function fetchOperationalReport(
  filters: OperationalReportFilters,
  signal?: AbortSignal,
): Promise<OperationalReport> {
  const query = new URLSearchParams({ from: filters.from, to: filters.to });
  if (filters.vehicleId) query.set('vehicleId', String(filters.vehicleId));
  if (filters.driverId) query.set('driverId', String(filters.driverId));
  const response = await appFetch(`${BASE}/reports/operations?${query.toString()}`, { signal });
  if (!response.ok) {
    let message = `Không thể tải báo cáo (HTTP ${response.status}).`;
    try {
      const problem = (await response.json()) as { detail?: string; title?: string };
      message = problem.detail || problem.title || message;
    } catch {
      /* Keep the HTTP fallback when the response is not JSON. */
    }
    throw new Error(message);
  }
  return response.json() as Promise<OperationalReport>;
}

export async function fetchOperationalReportDetail(
  filters: OperationalReportFilters,
  signal?: AbortSignal,
): Promise<OperationalReportDetail> {
  const query = new URLSearchParams({ from: filters.from, to: filters.to });
  if (filters.vehicleId) query.set('vehicleId', String(filters.vehicleId));
  if (filters.driverId) query.set('driverId', String(filters.driverId));
  const response = await appFetch(`${BASE}/reports/operations/detail?${query.toString()}`, { signal });
  if (!response.ok) {
    let message = `Không thể tải chi tiết báo cáo (HTTP ${response.status}).`;
    try {
      const problem = (await response.json()) as { detail?: string; title?: string };
      message = problem.detail || problem.title || message;
    } catch {
      /* Keep the HTTP fallback when the response is not JSON. */
    }
    throw new Error(message);
  }
  return response.json() as Promise<OperationalReportDetail>;
}
