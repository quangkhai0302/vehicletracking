import { toValue, watch, type MaybeRefOrGetter } from 'vue';
import type { OperationsSnapshot } from '@/features/tracking/types/operations';
import { notifySuccess, notifyWarning } from '@/shared/notifications/toast';
import { alertDetail } from '../utils/notificationPresentation';

export function useAdminIncidentNotifications(
  snapshot: MaybeRefOrGetter<OperationsSnapshot | null>,
  showReported = notifyWarning,
  showResolved = notifySuccess,
) {
  let initialized = false;
  const seen = new Set<number>();
  watch(() => toValue(snapshot), current => {
    if (!current) return;
    for (const item of current.notifications) {
      if (item.type !== 'SIMULATION_INCIDENT' && item.type !== 'SIMULATION_INCIDENT_RESOLVED') continue;
      if (initialized && !seen.has(item.id)) {
        const show = item.type === 'SIMULATION_INCIDENT_RESOLVED' ? showResolved : showReported;
        show(`${item.title}. ${alertDetail(item)}`, { toastId: `admin-incident-${item.id}`, autoClose: 7000 });
      }
      seen.add(item.id);
    }
    initialized = true;
  }, { immediate: true });
}
