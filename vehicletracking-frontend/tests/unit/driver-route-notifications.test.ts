import { expect, test, vi } from 'vitest';
import { effectScope, nextTick, shallowRef } from 'vue';
import { useDriverRouteNotifications } from '@/features/tracking/composables/useDriverRouteNotifications';
import type { OperationsSnapshot } from '@/features/tracking/types/operations';
import type { NotificationItem } from '@/features/reports/types/notifications';
const notification = (id: number): NotificationItem => ({ id, tripId: 7, vehicleId: 1, vehiclePlateNumber: 'TEST', revisionId: 17,
  type: 'DRIVER_ROUTE_CHANGED', severity: 'MAJOR', title: 'Tài xế đổi đường', reason: 'Tài xế A chọn đường mới', incidentId: null,
  affectedStopSequences: '2', baselineEtaSeconds: 60, revisedEtaSeconds: 70, createdAt: '2026-09-29T02:00:00Z', readAt: null });
test('admin receives each new driver route notification once, without replaying old inbox', async () => {
  const state = shallowRef<OperationsSnapshot>({ serverTime: '2026-09-29T02:00:00Z', positions: [], trips: [], simulations: [], checkIns: [], notifications: [notification(1)] });
  const show = vi.fn(), scope = effectScope(); scope.run(() => useDriverRouteNotifications(state, show));
  expect(show).not.toHaveBeenCalled();
  state.value = { ...state.value, notifications: [notification(2), notification(1)] }; await nextTick();
  expect(show).toHaveBeenCalledTimes(1); expect(show).toHaveBeenCalledWith(expect.stringContaining('Tài xế A'), expect.objectContaining({ toastId: 'driver-route-2' }));
  state.value = { ...state.value }; await nextTick(); expect(show).toHaveBeenCalledTimes(1);
  scope.stop(); state.value = { ...state.value, notifications: [notification(3)] }; await nextTick(); expect(show).toHaveBeenCalledTimes(1);
});
