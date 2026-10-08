import { expect, test, vi } from 'vitest';
import { effectScope, nextTick, shallowRef } from 'vue';
import { useAdminIncidentNotifications } from '@/features/reports/composables/useAdminIncidentNotifications';
import type { OperationsSnapshot } from '@/features/tracking/types/operations';
import type { NotificationItem } from '@/features/reports/types/notifications';
const notice = (id: number, type: NotificationItem['type']): NotificationItem => ({
  id, type, tripId: 7, vehicleId: 1, vehiclePlateNumber: '51B-12345', revisionId: null, severity: 'MAJOR',
  title: type === 'SIMULATION_INCIDENT_RESOLVED' ? 'Tài xế đã xử lý xong sự cố' : 'Xe gặp sự cố',
  reason: 'Xe đang dừng', incidentId: null, affectedStopSequences: '', baselineEtaSeconds: null, revisedEtaSeconds: null,
  createdAt: '2026-10-08T01:00:00Z', readAt: null, simulationIncidentResolutionNote: 'Đã thay lốp, tiếp tục chuyến',
});
const snapshot = (notifications: NotificationItem[]) => ({ notifications }) as OperationsSnapshot;
test('admin receives reported and resolved incident toasts once, skipping history and stopping on disposal', async () => {
  const source = shallowRef<OperationsSnapshot | null>(null), reported = vi.fn(), resolved = vi.fn();
  const scope = effectScope(); scope.run(() => useAdminIncidentNotifications(source, reported, resolved));
  source.value = snapshot([notice(1, 'SIMULATION_INCIDENT')]); await nextTick();
  expect(reported).not.toHaveBeenCalled();
  source.value = snapshot([notice(2, 'SIMULATION_INCIDENT'), notice(1, 'SIMULATION_INCIDENT')]); await nextTick();
  expect(reported).toHaveBeenCalledTimes(1); expect(reported).toHaveBeenLastCalledWith('Xe gặp sự cố. Xe đang dừng', expect.objectContaining({ toastId: 'admin-incident-2' }));
  source.value = snapshot([notice(3, 'SIMULATION_INCIDENT_RESOLVED'), notice(2, 'SIMULATION_INCIDENT')]); await nextTick();
  expect(resolved).toHaveBeenCalledWith('Tài xế đã xử lý xong sự cố. Đã thay lốp, tiếp tục chuyến', expect.objectContaining({ toastId: 'admin-incident-3' }));
  source.value = snapshot([notice(3, 'SIMULATION_INCIDENT_RESOLVED')]); await nextTick();
  expect(resolved).toHaveBeenCalledTimes(1);
  scope.stop(); source.value = snapshot([notice(4, 'SIMULATION_INCIDENT')]); await nextTick();
  expect(reported).toHaveBeenCalledTimes(1);
});
