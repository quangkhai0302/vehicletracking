import { effectScope, nextTick, shallowRef } from 'vue';
import { afterEach, expect, test, vi } from 'vitest';
import { useCheckInNotifications } from '@/features/tracking/composables/useCheckInNotifications';
import type { StopVisit } from '@/features/fleet/types/checkin';
import type { TripSummary } from '@/features/fleet/types/fleet';
import type { OperationsSnapshot } from '@/features/tracking/types/operations';
import { registerToastModal, syncToastLayer } from '@/shared/notifications/toastLayer';
import { errorMessage } from '@/shared/notifications/toast';

const trip: TripSummary = {
  id: 12,
  attemptNumber: 2,
  vehicleId: 3,
  vehiclePlateNumber: '51B-12345',
  vehicleType: 'CAR',
  routeId: 4,
  routeName: 'Tuyến kiểm thử',
  scheduledDepartureAt: '2026-09-24T01:00:00Z',
  plannedEndAt: '2026-09-24T02:00:00Z',
  startedAt: '2026-09-24T01:00:00Z',
  endedAt: null,
  createdAt: '2026-09-24T00:00:00Z',
  dispatchMode: 'ON_DEMAND',
  scheduleId: null,
  scheduleName: null,
  driver: null,
  status: 'IN_PROGRESS',
};

const visit = (id: number, stopSequence: number, detectedAt: string): StopVisit => ({
  id,
  tripId: trip.id,
  attemptNumber: trip.attemptNumber,
  stopSequence,
  source: 'SIMULATOR',
  evidenceKind: 'POINT',
  actualArrivalAt: detectedAt,
  simulatedArrivalAt: detectedAt,
  detectedAt,
  fromSampleId: null,
  toSampleId: id,
  evidenceFraction: 1,
  latitude: 10.8,
  longitude: 106.7,
});

const snapshot = (serverTime: string, visits: StopVisit[]): OperationsSnapshot => ({
  serverTime,
  positions: [],
  simulations: [],
  trips: [trip],
  checkIns: [
    {
      tripId: trip.id,
      revision: 1,
      nextStopSequence: null,
      awaitingExit: false,
      visits,
    },
  ],
  notifications: [],
});

const scopes: ReturnType<typeof effectScope>[] = [];

afterEach(() => {
  scopes.splice(0).forEach((scope) => scope.stop());
  document.querySelector('.Toastify')?.remove();
});

test('keeps the toast root above the top-most native modal and restores it on close', () => {
  const first = document.createElement('dialog');
  first.open = true;
  document.body.appendChild(first);
  const releaseFirst = registerToastModal(first);

  const toastRoot = document.createElement('div');
  toastRoot.className = 'Toastify';
  Object.defineProperty(toastRoot, 'showPopover', { configurable: true, value: undefined });
  document.body.appendChild(toastRoot);
  syncToastLayer();
  expect(toastRoot.parentElement).toBe(first);

  const second = document.createElement('dialog');
  second.open = true;
  document.body.appendChild(second);
  const releaseSecond = registerToastModal(second);
  expect(toastRoot.parentElement).toBe(second);

  releaseSecond();
  expect(toastRoot.parentElement).toBe(first);
  second.remove();

  releaseFirst();
  expect(toastRoot.parentElement).toBe(document.body);
  first.remove();
});

test('promotes the toast root to a non-modal popover when the browser supports it', () => {
  const modal = document.createElement('dialog');
  modal.open = true;
  document.body.appendChild(modal);

  const toastRoot = document.createElement('div');
  toastRoot.className = 'Toastify';
  const showPopover = vi.fn();
  const hidePopover = vi.fn();
  Object.defineProperties(toastRoot, {
    showPopover: { configurable: true, value: showPopover },
    hidePopover: { configurable: true, value: hidePopover },
  });
  document.body.appendChild(toastRoot);

  const release = registerToastModal(modal);
  expect(toastRoot.getAttribute('popover')).toBe('manual');
  expect(toastRoot.parentElement).toBe(document.body);
  expect(hidePopover).toHaveBeenCalledOnce();
  expect(showPopover).toHaveBeenCalledOnce();

  release();
  modal.remove();
});

test('preserves concrete strings and extracts complete API problem details', () => {
  expect(errorMessage('Biển số đã tồn tại. Vui lòng kiểm tra lại.')).toBe(
    'Biển số đã tồn tại. Vui lòng kiểm tra lại.',
  );
  expect(errorMessage(new Error('Tài xế đang chạy một chuyến khác.'))).toBe(
    'Tài xế đang chạy một chuyến khác.',
  );
  expect(
    errorMessage({
      title: 'Validation Failed',
      detail: 'Dữ liệu gửi lên chưa hợp lệ.',
      errors: [
        { field: 'licenseNumber', message: 'Số GPLX đã tồn tại.' },
        { field: 'phoneNumber', defaultMessage: 'Số điện thoại không hợp lệ.' },
      ],
    }),
  ).toBe(
    'Dữ liệu gửi lên chưa hợp lệ.\nlicenseNumber: Số GPLX đã tồn tại.\nphoneNumber: Số điện thoại không hợp lệ.',
  );
  expect(errorMessage({ violations: { name: 'Không được để trống.' } })).toBe(
    'name: Không được để trống.',
  );
  expect(errorMessage(null)).toBe('Đã xảy ra lỗi. Vui lòng thử lại.');
});

test('only announces new automatic check-ins and does not replay the initial history', async () => {
  const oldVisit = visit(1, 1, '2026-09-24T01:00:00Z');
  const current = shallowRef(snapshot('2026-09-24T01:01:00Z', [oldVisit]));
  const notify = vi.fn();
  const scope = effectScope();
  scopes.push(scope);
  scope.run(() => useCheckInNotifications(current, notify));

  await nextTick();
  expect(notify).not.toHaveBeenCalled();

  const newVisit = visit(2, 2, '2026-09-24T01:01:30Z');
  current.value = snapshot('2026-09-24T01:02:00Z', [oldVisit, newVisit]);
  await nextTick();

  expect(notify).toHaveBeenCalledOnce();
  expect(notify).toHaveBeenCalledWith(
    'Xe 51B-12345 đã tự động check-in điểm dừng 2 · Chuyến #12.',
    { autoClose: 5000, toastId: 'check-in-12:2:2' },
  );

  current.value = snapshot('2026-09-24T01:03:00Z', [oldVisit, newVisit]);
  await nextTick();
  expect(notify).toHaveBeenCalledOnce();
});

test('ignores a historical visit first returned by a later snapshot', async () => {
  const current = shallowRef(snapshot('2026-09-24T02:00:00Z', []));
  const notify = vi.fn();
  const scope = effectScope();
  scopes.push(scope);
  scope.run(() => useCheckInNotifications(current, notify));

  current.value = snapshot('2026-09-24T02:01:00Z', [visit(3, 1, '2026-09-24T01:30:00Z')]);
  await nextTick();

  expect(notify).not.toHaveBeenCalled();
});
