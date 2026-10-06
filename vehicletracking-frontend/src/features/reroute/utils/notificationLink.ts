import type { NotificationItem } from '@/features/reports/types/notifications';
export function notificationMonitoringLink(item: Pick<NotificationItem, 'tripId' | 'revisionId' | 'type'>): string {
  const comparison = (item.type === 'REROUTE_CREATED' || item.type === 'DRIVER_ROUTE_CHANGED')
    && item.revisionId != null && Number.isSafeInteger(item.revisionId) && item.revisionId > 0;
  return `/operations?tripId=${item.tripId}${comparison ? `&revisionId=${item.revisionId}` : ''}`;
}
