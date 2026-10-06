import type { NotificationItem } from '../types/notifications';
const formatDistance = (meters?: number | null) =>
  meters == null ? null : `${Math.round(meters)} m`;
export const formatDateTime = (value: string) =>
  new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'short',
    timeStyle: 'short',
    timeZone: 'Asia/Ho_Chi_Minh',
  }).format(new Date(value));
export function alertDetail(item: NotificationItem) {
  if (item.type === 'SIMULATION_INCIDENT') return item.simulationIncidentDetail || item.reason;
  if (item.type !== 'OFF_ROUTE_DETECTED') return item.reason;
  const distance = formatDistance(item.measuredDistanceMeters),
    threshold = formatDistance(item.thresholdDistanceMeters);
  const duration = item.breachDurationSeconds == null ? null : `${item.breachDurationSeconds}s`;
  return (
    [
      distance && `Khoảng cách ${distance}`,
      threshold && `ngưỡng ${threshold}`,
      duration && `duy trì ${duration}`,
    ]
      .filter(Boolean)
      .join(' · ') || item.reason
  );
}
