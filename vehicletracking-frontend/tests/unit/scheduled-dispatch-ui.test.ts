import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { nextTick } from 'vue';
import AdminDispatchPanel from '@/features/dispatch/components/AdminDispatchPanel.vue';
import DriverDispatchWorkspace from '@/features/dispatch/components/DriverDispatchWorkspace.vue';
import * as api from '@/features/dispatch/api/dispatch';
import type { TripSummary } from '@/features/fleet/types/fleet';
import type { DispatchDetail, DriverDispatchDetail } from '@/features/dispatch/types/dispatch';

vi.mock('@/features/dispatch/api/dispatch', () => ({
  DispatchApiError: class extends Error {
    constructor(public status: number, public code: string | null, message: string) { super(message); }
  },
  fetchDispatchDetail: vi.fn(), updateTripDispatchPolicy: vi.fn(), overrideTripStart: vi.fn(),
  fetchDriverDispatch: vi.fn(), readyForTrip: vi.fn(), reportUnavailable: vi.fn(),
  fetchDispatchOffers: vi.fn(), acceptDispatchOffer: vi.fn(), declineDispatchOffer: vi.fn(),
  fetchDriverAssignmentRequests: vi.fn(), acceptDriverAssignmentRequest: vi.fn(),
  declineDriverAssignmentRequest: vi.fn(),
  fetchDispatchInbox: vi.fn(), readDispatchInboxItem: vi.fn(),
}));
const wrappers: VueWrapper[] = [];
const trip: TripSummary = {
  id: 7, vehicleId: 2, vehiclePlateNumber: '51B12345', vehicleType: 'CAR',
  routeId: 3, routeName: 'Tuyến A', status: 'SCHEDULED',
  scheduledDepartureAt: '2026-10-01T08:00:00Z', plannedEndAt: '2026-10-01T09:00:00Z',
  startedAt: null, endedAt: null, createdAt: '2026-09-30T08:00:00Z',
  dispatchMode: 'FIXED_SCHEDULE', scheduleId: 4, scheduleName: 'Lịch A', driver: null,
  dispatch: { startMode: 'AUTO_IF_READY', state: 'WAITING_READY', attentionCode: null, readyAt: null, revision: 1 },
};
const adminDetail: DispatchDetail = {
  tripId: 7, summary: trip.dispatch!, primaryDriverId: 8, currentDriverId: 8,
  candidates: [], activeOffer: null, history: [],
};
const driverDetail: DriverDispatchDetail = {
  tripId: 7, startMode: 'AUTO_IF_READY', state: 'WAITING_READY', attentionCode: null,
  readyAt: null, cutoffAt: '2026-10-01T08:15:00Z', revision: 1,
  canReady: true, canReportUnavailable: true,
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.fetchDispatchDetail).mockResolvedValue(adminDetail);
  vi.mocked(api.fetchDriverDispatch).mockResolvedValue(driverDetail);
  vi.mocked(api.fetchDispatchOffers).mockResolvedValue([]);
  vi.mocked(api.fetchDriverAssignmentRequests).mockResolvedValue([]);
  vi.mocked(api.fetchDispatchInbox).mockResolvedValue([]);
  vi.mocked(api.readyForTrip).mockResolvedValue({ ...driverDetail, state: 'READY', revision: 2 });
  vi.mocked(api.updateTripDispatchPolicy).mockResolvedValue(adminDetail);
});
afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount());
  vi.useRealTimers();
});

test('driver can confirm READY from assigned card; polling is cleaned up', async () => {
  const wrapper = mount(DriverDispatchWorkspace, { props: { trips: [trip] } });
  wrappers.push(wrapper);
  await flushPromises();
  expect(wrapper.text()).toContain('Xác nhận và lời mời');
  expect(wrapper.text()).toContain('Chưa xác nhận');
  await wrapper.find('.driver-dispatch-actions .dispatch-primary').trigger('click');
  await flushPromises();
  expect(api.readyForTrip).toHaveBeenCalledWith(7, 1);
  expect(wrapper.emitted('changed')).toHaveLength(1);
  const signal = vi.mocked(api.fetchDispatchOffers).mock.calls[0]![0]!;
  wrapper.unmount();
  wrappers.pop();
  expect(signal.aborted).toBe(true);
});

test('candidate sees offer metadata and can accept without a trip navigation link', async () => {
  vi.mocked(api.fetchDispatchOffers).mockResolvedValue([{
    offerId: 'offer-7', tripId: 7, routeName: 'Tuyến A', vehiclePlate: '51B12345',
    scheduledDepartureAt: '2026-10-01T08:00:00Z', cutoffAt: '2026-10-01T08:15:00Z',
    expiresAt: '2026-10-01T07:59:00Z', revision: 5,
  }]);
  vi.mocked(api.acceptDispatchOffer).mockResolvedValue({ tripId: 7, status: 'ACCEPTED', dispatch: driverDetail });
  const wrapper = mount(DriverDispatchWorkspace, { props: { trips: [] } });
  wrappers.push(wrapper);
  await flushPromises();
  expect(wrapper.text()).toContain('Lời mời nhận chuyến');
  expect(wrapper.find('a[href*="/navigate"]').exists()).toBe(false);
  await wrapper.find('.driver-dispatch-offers .dispatch-primary').trigger('click');
  await flushPromises();
  expect(api.acceptDispatchOffer).toHaveBeenCalledWith('offer-7', 5);
  expect(wrapper.emitted('changed')).toHaveLength(1);
});

test('candidate sees direct on-demand request and can accept it before the trip appears', async () => {
  vi.mocked(api.fetchDriverAssignmentRequests).mockResolvedValue([{
    requestId: 'request-7', tripId: 7, routeName: 'Tuyến tức thời', vehiclePlate: '51B12345',
    tripCreatedAt: '2026-10-01T07:40:00Z', requestedAt: '2026-10-01T07:45:00Z',
  }]);
  vi.mocked(api.acceptDriverAssignmentRequest).mockResolvedValue({
    requestId: 'request-7', status: 'ACCEPTED', tripId: 7,
  });
  const wrapper = mount(DriverDispatchWorkspace, { props: { trips: [] } });
  wrappers.push(wrapper);
  await flushPromises();
  expect(wrapper.text()).toContain('Yêu cầu nhận chuyến tức thời');
  expect(wrapper.text()).toContain('Tuyến tức thời');
  expect(wrapper.find('a[href*="/navigate"]').exists()).toBe(false);
  await wrapper.find('.driver-assignment-requests .dispatch-primary').trigger('click');
  await flushPromises();
  expect(api.acceptDriverAssignmentRequest).toHaveBeenCalledWith('request-7');
  expect(wrapper.emitted('changed')).toHaveLength(1);
});

test('direct assignment decline requires a reason and sends it to the backend', async () => {
  vi.mocked(api.fetchDriverAssignmentRequests).mockResolvedValue([{
    requestId: 'request-8', tripId: 8, routeName: 'Tuyến B', vehiclePlate: '51B88888',
    tripCreatedAt: '2026-10-01T07:40:00Z', requestedAt: '2026-10-01T07:45:00Z',
  }]);
  vi.mocked(api.declineDriverAssignmentRequest).mockResolvedValue({
    requestId: 'request-8', status: 'DECLINED', tripId: 8,
  });
  const wrapper = mount(DriverDispatchWorkspace, {
    props: { trips: [] },
    global: {
      stubs: {
        FleetConfirmDialog: {
          props: ['confirmDisabled', 'onConfirm', 'onClose', 'busy', 'confirmLabel'],
          template: '<div class="assignment-confirm"><slot /><button class="danger-action" :disabled="busy || confirmDisabled" @click="onConfirm">{{ confirmLabel }}</button></div>',
        },
      },
    },
  });
  wrappers.push(wrapper);
  await flushPromises();
  await wrapper.find('.driver-assignment-requests .dispatch-secondary').trigger('click');
  await nextTick();
  const confirm = wrapper.find('.assignment-confirm');
  expect(confirm.exists()).toBe(true);
  const confirmButton = confirm.find('.danger-action');
  expect(confirmButton.attributes('disabled')).toBeDefined();
  await confirm.find('textarea').setValue('Không thể nhận chuyến');
  await confirm.findAll('button').find((button) => button.text().includes('Xác nhận từ chối'))!.trigger('click');
  await flushPromises();
  expect(api.declineDriverAssignmentRequest).toHaveBeenCalledWith('request-8', 'Không thể nhận chuyến');
});

test('driver inbox and offers remain visible when reassignment revokes trip detail access', async () => {
  vi.mocked(api.fetchDriverDispatch).mockRejectedValue(new api.DispatchApiError(404, null, 'Không tìm thấy chuyến.'));
  vi.mocked(api.fetchDispatchOffers).mockResolvedValue([{
    offerId: 'offer-8', tripId: 8, routeName: 'Tuyến B', vehiclePlate: '51B88888',
    scheduledDepartureAt: '2026-10-01T08:00:00Z', cutoffAt: '2026-10-01T08:15:00Z',
    expiresAt: '2026-10-01T07:59:00Z', revision: 2,
  }]);
  const wrapper = mount(DriverDispatchWorkspace, { props: { trips: [trip] } });
  wrappers.push(wrapper);
  await flushPromises();
  expect(wrapper.text()).toContain('Lời mời nhận chuyến');
  expect(wrapper.find('[role="alert"]').exists()).toBe(false);
});

test('admin sees dispatch state and can change trip-only policy with revision', async () => {
  const wrapper = mount(AdminDispatchPanel, {
    props: { trip, drivers: [], onChanged: vi.fn() },
  });
  wrappers.push(wrapper);
  await flushPromises();
  expect(wrapper.text()).toContain('Chưa xác nhận');
  await wrapper.find('.dispatch-policy-editor select').setValue('MANUAL');
  await wrapper.find('.dispatch-policy-editor .dispatch-primary').trigger('click');
  await flushPromises();
  expect(api.updateTripDispatchPolicy).toHaveBeenCalledWith(7, {
    expectedRevision: 1, startMode: 'MANUAL', backupEnabled: false, backupDriverIds: [],
  });
});

test('draft AUTO does not expose override before policy is saved', async () => {
  const manualTrip: TripSummary = {
    ...trip,
    scheduledDepartureAt: '2020-01-01T08:00:00Z',
    dispatch: { ...trip.dispatch!, startMode: 'MANUAL', state: 'MANUAL' },
  };
  vi.mocked(api.fetchDispatchDetail).mockResolvedValue({ ...adminDetail, summary: manualTrip.dispatch! });
  const wrapper = mount(AdminDispatchPanel, {
    props: { trip: manualTrip, drivers: [], onChanged: vi.fn() },
  });
  wrappers.push(wrapper);
  await flushPromises();
  await wrapper.find('.dispatch-policy-editor select').setValue('AUTO_IF_READY');
  expect(wrapper.find('.dispatch-policy-actions .dispatch-secondary').exists()).toBe(false);
});

test('invalid policy state shows a readable error and refreshes the trip', async () => {
  vi.mocked(api.updateTripDispatchPolicy).mockRejectedValue(
    new api.DispatchApiError(409, 'DISPATCH_INVALID_STATE', 'DISPATCH_INVALID_STATE'),
  );
  const onChanged = vi.fn();
  const wrapper = mount(AdminDispatchPanel, { props: { trip, drivers: [], onChanged } });
  wrappers.push(wrapper);
  await flushPromises();
  await wrapper.find('.dispatch-policy-editor select').setValue('MANUAL');
  await wrapper.find('.dispatch-policy-actions .dispatch-primary').trigger('click');
  await flushPromises();
  expect(wrapper.find('[role="alert"]').text()).toContain('Không thể đổi chính sách');
  expect(wrapper.find('[role="alert"]').text()).not.toContain('DISPATCH_INVALID_STATE');
  expect(wrapper.find('[role="alert"]').text()).toContain('Tải lại chi tiết');
  expect(onChanged).toHaveBeenCalled();
});

test('admin draft keeps its revision while polling receives a newer dispatch', async () => {
  vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
  const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
  try {
    const wrapper = mount(AdminDispatchPanel, {
      props: { trip, drivers: [], onChanged: vi.fn() },
    });
    wrappers.push(wrapper);
    await flushPromises();
    await wrapper.find('.dispatch-policy-editor select').setValue('MANUAL');
    vi.mocked(api.fetchDispatchDetail).mockResolvedValue({
      ...adminDetail,
      summary: { ...adminDetail.summary, state: 'READY', revision: 2 },
    });
    await vi.advanceTimersByTimeAsync(5000);
    await flushPromises();
    await wrapper.find('.dispatch-policy-actions .dispatch-primary').trigger('click');
    await flushPromises();
    expect(api.updateTripDispatchPolicy).toHaveBeenCalledWith(7, expect.objectContaining({
      expectedRevision: 1,
    }));
  } finally {
    visibility.mockRestore();
  }
});
