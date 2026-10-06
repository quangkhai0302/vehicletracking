import { appFetch } from '@/shared/api/http';
import type { RouteComparison } from '../types/comparison';
const BASE = `${(import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')}/api/v1`;
export async function fetchRouteComparison(tripId: number, revisionId: number, signal: AbortSignal): Promise<RouteComparison> {
  let response: Response;
  try {
    response = await appFetch(`${BASE}/trips/${tripId}/revisions/${revisionId}/comparison`, { signal });
  } catch (failure) {
    if (signal.aborted) throw failure;
    throw new Error('Chưa tải được lịch sử đổi tuyến. Vui lòng thử lại.');
  }
  if (!response.ok) throw new Error(response.status === 404
    ? 'Không tìm thấy lần đổi tuyến của chuyến này.' : 'Chưa tải được lịch sử đổi tuyến. Vui lòng thử lại.');
  let data: RouteComparison;
  try { data = await response.json() as RouteComparison; }
  catch { throw new Error('Không đọc được lịch sử đổi tuyến. Vui lòng thử lại.'); }
  if (data.tripId !== tripId || data.revisionId !== revisionId)
    throw new Error('Lịch sử đổi tuyến không khớp với thông báo đã chọn.');
  return data;
}
