import { afterEach, expect, test, vi } from 'vitest';
import { effectScope, nextTick, shallowRef } from 'vue';
import { flushPromises } from '@vue/test-utils';
import { useAdminNotifications } from '@/features/reports/composables/useAdminNotifications';
import type { NotificationItem } from '@/features/reports/types/notifications';

vi.mock('@/features/reports/api/notifications', () => ({
  deleteNotification: vi.fn(),
  fetchNotifications: vi.fn().mockResolvedValue([]),
  markAllNotificationsRead: vi.fn(),
  markNotificationRead: vi.fn(),
  acknowledgeIncident: vi.fn(),
  resolveIncident: vi.fn(),
}));
vi.mock('@/shared/notifications/toast', () => ({ notifyError: vi.fn(), notifySuccess: vi.fn() }));

const notice: NotificationItem = {
  id: 21,
  tripId: 9,
  vehicleId: 3,
  vehiclePlateNumber: '51B12345',
  revisionId: null,
  type: 'DIRECT_ASSIGNMENT_ACCEPTED',
  severity: 'MAJOR',
  title: 'Tài xế đã nhận chuyến',
  reason: 'Nguyễn Văn A đã xác nhận nhận chuyến.',
  incidentId: null,
  affectedStopSequences: '',
  baselineEtaSeconds: null,
  revisedEtaSeconds: null,
  createdAt: '2026-10-07T01:00:00Z',
  readAt: null,
};

const scopes: ReturnType<typeof effectScope>[] = [];
afterEach(() => scopes.splice(0).forEach((scope) => scope.stop()));

test('admin bell items and unread count update from the live operations snapshot', async () => {
  const source = shallowRef<NotificationItem[] | null>(null);
  const scope = effectScope();
  scopes.push(scope);
  let state!: ReturnType<typeof useAdminNotifications>;
  scope.run(() => { state = useAdminNotifications(source); });
  await flushPromises();
  expect(state.items.value).toEqual([]);
  source.value = [notice];
  await nextTick();
  expect(state.items.value).toEqual([notice]);
  expect(state.unread.value).toBe(1);
  state.typeFilter.value = 'DISPATCH';
  expect(state.filtered.value).toEqual([notice]);
});
