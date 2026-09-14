import type { NotificationItem, RouteRevision } from '../types/notifications';
const BASE = `${(import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')}/api/v1`;
export async function markNotificationRead(id: number): Promise<NotificationItem> {
  const response = await fetch(`${BASE}/notifications/${id}/read`, { method: 'POST' });
  if (!response.ok) throw new Error(`Không thể cập nhật thông báo (HTTP ${response.status}).`);
  return response.json() as Promise<NotificationItem>;
}
export async function markAllNotificationsRead(): Promise<{ updated: number }> {
  const response = await fetch(`${BASE}/notifications/read-all`, { method: 'POST' });
  if (!response.ok) throw new Error(`Không thể đánh dấu thông báo (HTTP ${response.status}).`);
  return response.json() as Promise<{ updated: number }>;
}
export async function deleteNotification(id: number): Promise<void> {
  const response = await fetch(`${BASE}/notifications/${id}`, { method: 'DELETE' });
  if (!response.ok) throw new Error(`Không thể xóa thông báo (HTTP ${response.status}).`);
}
export async function fetchNotifications(unreadOnly = false, signal?: AbortSignal): Promise<NotificationItem[]> {
  const response = await fetch(`${BASE}/notifications?unreadOnly=${unreadOnly}`, { signal });
  if (!response.ok) throw new Error(`Không thể tải thông báo (HTTP ${response.status}).`);
  return response.json() as Promise<NotificationItem[]>;
}
export async function fetchTripRevisions(tripId: number, signal?: AbortSignal): Promise<RouteRevision[]> {
  const response = await fetch(`${BASE}/trips/${tripId}/revisions`, { signal });
  if (!response.ok) throw new Error(`Không thể tải phiên bản tuyến (HTTP ${response.status}).`);
  return response.json() as Promise<RouteRevision[]>;
}
export async function supersedeTripRevision(tripId: number, revisionId: number): Promise<RouteRevision> {
  const response = await fetch(`${BASE}/trips/${tripId}/revisions/${revisionId}/supersede`, { method: 'POST' });
  if (!response.ok) throw new Error(`Không thể ngừng hiệu lực phiên bản tuyến (HTTP ${response.status}).`);
  return response.json() as Promise<RouteRevision>;
}
