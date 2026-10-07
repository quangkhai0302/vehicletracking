import { toValue, watch, type MaybeRefOrGetter } from 'vue';
import type { OperationsSnapshot } from '@/features/tracking/types/operations';
import type { NotificationItem } from '../types/notifications';
import { notifySuccess, notifyWarning } from '@/shared/notifications/toast';

const assignmentTypes = new Set<NotificationItem['type']>([
  'DIRECT_ASSIGNMENT_ACCEPTED',
  'DIRECT_ASSIGNMENT_DECLINED',
]);

export function useAdminAssignmentNotifications(
  snapshot: MaybeRefOrGetter<OperationsSnapshot | null>,
  showAccepted = notifySuccess,
  showDeclined = notifyWarning,
) {
  let initialized = false;
  const seen = new Set<number>();
  watch(
    () => toValue(snapshot),
    (current) => {
      if (!current) return;
      for (const item of current.notifications) {
        if (!assignmentTypes.has(item.type)) continue;
        if (initialized && !seen.has(item.id)) {
          const show = item.type === 'DIRECT_ASSIGNMENT_ACCEPTED' ? showAccepted : showDeclined;
          show(`${item.title}. ${item.reason}`, {
            toastId: `admin-assignment-${item.id}`,
            autoClose: 7000,
          });
        }
        seen.add(item.id);
      }
      initialized = true;
    },
    { immediate: true },
  );
}
