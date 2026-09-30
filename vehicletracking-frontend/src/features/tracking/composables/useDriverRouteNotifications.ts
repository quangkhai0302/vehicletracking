import { toValue, watch, type MaybeRefOrGetter } from 'vue';
import type { OperationsSnapshot } from '@/features/tracking/types/operations';
import { notifyInfo } from '@/shared/notifications/toast';

export function useDriverRouteNotifications(snapshot: MaybeRefOrGetter<OperationsSnapshot | null>, show = notifyInfo) {
  let initialized = false;
  const seen = new Set<number>();
  watch(() => toValue(snapshot), current => {
    if (!current) return;
    for (const item of current.notifications.filter(n => n.type === 'DRIVER_ROUTE_CHANGED')) {
      if (initialized && !seen.has(item.id)) show(`${item.title}. ${item.reason}`, { toastId: `driver-route-${item.id}`, autoClose: 7000 });
      seen.add(item.id);
    }
    initialized = true;
  }, { immediate: true });
}
