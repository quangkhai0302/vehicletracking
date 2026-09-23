import type { NotificationItem } from './notifications';

export interface DashboardSummary {
  serverTime: string;
  activeVehicleCount: number;
  activeDriverCount: number;
  tripsInProgress: number;
  scheduledTrips: number;
  completedTrips: number;
  cancelledTrips: number;
  overdueTrips: number;
  offRouteVehicleCount: number;
  unreadAlertCount: number;
  pendingAlerts: NotificationItem[];
}
