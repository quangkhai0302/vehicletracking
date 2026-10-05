import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { nextTick } from 'vue';
import DriverDispatchWorkspace from '@/features/dispatch/components/DriverDispatchWorkspace.vue';
import * as api from '@/features/dispatch/api/dispatch';

vi.mock('@/features/dispatch/api/dispatch', () => ({
  DispatchApiError: class extends Error {
    constructor(public status: number, public code: string | null, message: string) { super(message); }
  },
  fetchDriverAssignmentRequests: vi.fn(), acceptDriverAssignmentRequest: vi.fn(),
  declineDriverAssignmentRequest: vi.fn(),
  fetchDispatchInbox: vi.fn(), readDispatchInboxItem: vi.fn(),
}));
const wrappers: VueWrapper[] = [];
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.fetchDriverAssignmentRequests).mockResolvedValue([]);
  vi.mocked(api.fetchDispatchInbox).mockResolvedValue([]);
});
afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount());
  vi.useRealTimers();
});

test('empty driver workspace keeps inbox and has no backup invitation controls', async () => {
  const wrapper = mount(DriverDispatchWorkspace, {});
  wrappers.push(wrapper);
  await flushPromises();
  expect(wrapper.text()).toContain('Chưa có yêu cầu nhận chuyến mới.');
  expect(wrapper.text()).toContain('Hộp công việc');
  expect(wrapper.text()).not.toContain('dự phòng');
  expect(wrapper.text()).not.toContain('Lời mời nhận chuyến');
  expect(wrapper.find('.driver-dispatch-offers').exists()).toBe(false);
  expect(api.fetchDispatchInbox).toHaveBeenCalledTimes(1);
  expect(api.fetchDriverAssignmentRequests).toHaveBeenCalledTimes(1);
});

test('driver sees direct on-demand request and can accept it before the trip appears', async () => {
  vi.mocked(api.fetchDriverAssignmentRequests).mockResolvedValue([{
    requestId: 'request-7', tripId: 7, routeName: 'Tuyến tức thời', vehiclePlate: '51B12345',
    tripCreatedAt: '2026-10-01T07:40:00Z', requestedAt: '2026-10-01T07:45:00Z',
  }]);
  vi.mocked(api.acceptDriverAssignmentRequest).mockResolvedValue({
    requestId: 'request-7', status: 'ACCEPTED', tripId: 7,
  });
  const wrapper = mount(DriverDispatchWorkspace, {});
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

test('inbox remains usable when fetching direct requests fails', async () => {
  vi.mocked(api.fetchDriverAssignmentRequests).mockRejectedValue(new Error('Requests unavailable'));
  vi.mocked(api.fetchDispatchInbox).mockResolvedValue([{
    id: 5, type: 'DIRECT_ASSIGNMENT_REQUESTED', title: 'Yêu cầu nhận chuyến #7', detail: null,
    tripId: 7, offerId: null, createdAt: '2026-10-01T07:40:00Z', readAt: null,
  }]);
  const wrapper = mount(DriverDispatchWorkspace);
  wrappers.push(wrapper);
  await flushPromises();
  expect(wrapper.text()).toContain('Yêu cầu nhận chuyến #7');
  expect(wrapper.get('[role="alert"]').text()).toContain('Không thể tải yêu cầu nhận chuyến.');
  await wrapper.get('.driver-dispatch-inbox button').trigger('click');
  await flushPromises();
  expect(api.readDispatchInboxItem).toHaveBeenCalledWith(5);
});

test('workspace has no scheduled ready controls and cleans up polling and requests', async () => {
  vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
  const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
  try {
    const wrapper = mount(DriverDispatchWorkspace);
    wrappers.push(wrapper);
    await flushPromises();
    expect(wrapper.find('.driver-dispatch-cards').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('Sẵn sàng');
    expect(wrapper.text()).not.toContain('Báo bận');
    await vi.advanceTimersByTimeAsync(5000);
    await flushPromises();
    expect(api.fetchDispatchInbox).toHaveBeenCalledTimes(2);
    const calls = vi.mocked(api.fetchDispatchInbox).mock.calls;
    const signal = calls[calls.length - 1]![0]!;
    wrapper.unmount();
    wrappers.pop();
    expect(signal.aborted).toBe(true);
    await vi.advanceTimersByTimeAsync(5000);
    expect(api.fetchDispatchInbox).toHaveBeenCalledTimes(2);
  } finally {
    visibility.mockRestore();
  }
});
