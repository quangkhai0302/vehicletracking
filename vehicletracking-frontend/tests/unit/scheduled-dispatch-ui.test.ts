import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { nextTick, type Component } from 'vue';
import DriverDispatchWorkspace from '@/features/dispatch/components/DriverDispatchWorkspace.vue';
import * as api from '@/features/dispatch/api/dispatch';
import type {
  AssignmentActionResponse,
  DriverAssignmentRequest,
} from '@/features/dispatch/types/dispatch';

vi.mock('@/features/dispatch/api/dispatch', () => ({
  DispatchApiError: class extends Error {
    constructor(
      public status: number,
      public code: string | null,
      message: string,
    ) {
      super(message);
    }
  },
  fetchDriverAssignmentRequests: vi.fn(),
  acceptDriverAssignmentRequest: vi.fn(),
  declineDriverAssignmentRequest: vi.fn(),
  fetchDispatchInbox: vi.fn(),
  readDispatchInboxItem: vi.fn(),
  deleteDispatchInboxItem: vi.fn(),
}));
const wrappers: VueWrapper[] = [];
function render(dialogStub?: Component) {
  const wrapper = mount(DriverDispatchWorkspace, {
    props: { accountName: 'Nguyễn Văn D' },
    attachTo: document.body,
    global: {
      stubs: { Teleport: true, ...(dialogStub ? { FleetConfirmDialog: dialogStub } : {}) },
    },
  });
  wrappers.push(wrapper);
  return wrapper;
}
async function openNotifications(wrapper: VueWrapper) {
  await wrapper.get('.driver-notification-trigger').trigger('click');
  await flushPromises();
}
const pending = {
  requestId: 'request-7',
  tripId: 7,
  routeName: 'Tuyến tức thời',
  vehiclePlate: '51B12345',
  tripCreatedAt: '2026-10-01T07:40:00Z',
  requestedAt: '2026-10-01T07:45:00Z',
};
const notice = {
  id: 5,
  type: 'DIRECT_ASSIGNMENT_REQUESTED',
  title: 'Yêu cầu nhận chuyến #7',
  detail: null,
  tripId: 7,
  offerId: null,
  assignmentRequestId: 'request-7',
  createdAt: '2026-10-01T07:40:00Z',
  readAt: null,
};
const dialogStub = {
  props: ['confirmDisabled', 'onConfirm', 'onClose', 'busy', 'confirmLabel'],
  template:
    '<div class="assignment-confirm"><slot /><button class="cancel-action" @click="onClose">Quay lại</button><button class="danger-action" :disabled="busy || confirmDisabled" @click="onConfirm">{{ confirmLabel }}</button></div>',
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.fetchDriverAssignmentRequests).mockResolvedValue([]);
  vi.mocked(api.fetchDispatchInbox).mockResolvedValue([]);
  vi.mocked(api.deleteDispatchInboxItem).mockResolvedValue(undefined);
});
afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount());
  document.body.replaceChildren();
  vi.useRealTimers();
});

test('notifications and account actions open from separate header buttons', async () => {
  const wrapper = render();
  await flushPromises();
  expect(wrapper.findAll('button')).toHaveLength(2);
  expect(wrapper.get('.driver-account-trigger').attributes('aria-label')).toContain(
    'Tài khoản Nguyễn Văn D',
  );
  await wrapper.get('.driver-account-trigger').trigger('click');
  expect(wrapper.get('.driver-account-identity').text()).toBe('Nguyễn Văn D · Tài xế');
  expect(wrapper.get('.driver-account-actions').text()).toContain('Đổi mật khẩu');
  expect(wrapper.get('.driver-account-actions').text()).toContain('Đăng xuất');
  expect(wrapper.get('.driver-account-actions').text()).not.toContain('Thông báo');
  await wrapper.get('[aria-label="Đóng tài khoản"]').trigger('click');
  await wrapper.get('.driver-notification-trigger').trigger('click');
  expect(wrapper.get('[role="dialog"] h2').text()).toBe('Thông báo');
  expect(api.fetchDispatchInbox).toHaveBeenCalledTimes(1);
  expect(api.fetchDriverAssignmentRequests).toHaveBeenCalledTimes(1);
  await wrapper.get('[aria-label="Đóng thông báo"]').trigger('click');
  await wrapper.get('.driver-account-trigger').trigger('click');
  await wrapper
    .findAll('.driver-account-actions button')
    .find((button) => button.text() === 'Đổi mật khẩu')!
    .trigger('click');
  expect(wrapper.emitted('passwordChange')).toHaveLength(1);
  expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
  await wrapper.get('.driver-account-trigger').trigger('click');
  expect(wrapper.get('[role="dialog"] h2').text()).toBe('Tài khoản');
  await wrapper.get('.driver-account-signout').trigger('click');
  expect(wrapper.emitted('signOut')).toHaveLength(1);
  expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
});

test('notifications stay collapsed until the separate bell opens the empty inbox', async () => {
  const wrapper = render();
  await flushPromises();
  expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
  expect(wrapper.find('.driver-notifications-badge').exists()).toBe(false);
  expect(wrapper.get('.driver-notification-trigger').attributes('aria-expanded')).toBe('false');
  await openNotifications(wrapper);
  expect(wrapper.get('.driver-notification-trigger').attributes('aria-expanded')).toBe('true');
  expect(wrapper.text()).toContain('Chưa có thông báo hoặc yêu cầu nhận chuyến mới.');
  expect(wrapper.text()).toContain('Hộp công việc');
  expect(wrapper.text()).not.toContain('dự phòng');
  expect(wrapper.text()).not.toContain('Lời mời nhận chuyến');
  expect(wrapper.find('.driver-dispatch-offers').exists()).toBe(false);
  expect(api.fetchDispatchInbox).toHaveBeenCalledTimes(1);
  expect(api.fetchDriverAssignmentRequests).toHaveBeenCalledTimes(1);
});

test('driver sees direct on-demand request and can accept it before the trip appears', async () => {
  vi.mocked(api.fetchDriverAssignmentRequests).mockResolvedValue([
    {
      requestId: 'request-7',
      tripId: 7,
      routeName: 'Tuyến tức thời',
      vehiclePlate: '51B12345',
      tripCreatedAt: '2026-10-01T07:40:00Z',
      requestedAt: '2026-10-01T07:45:00Z',
    },
  ]);
  vi.mocked(api.acceptDriverAssignmentRequest).mockResolvedValue({
    requestId: 'request-7',
    status: 'ACCEPTED',
    tripId: 7,
  });
  const wrapper = render();
  await flushPromises();
  await openNotifications(wrapper);
  expect(wrapper.text()).toContain('Yêu cầu nhận chuyến tức thời');
  expect(wrapper.text()).toContain('Tuyến tức thời');
  expect(wrapper.find('a[href*="/navigate"]').exists()).toBe(false);
  await wrapper.find('.driver-assignment-requests .dispatch-primary').trigger('click');
  await flushPromises();
  expect(api.acceptDriverAssignmentRequest).toHaveBeenCalledWith('request-7');
  expect(wrapper.emitted('changed')).toHaveLength(1);
});

test('direct assignment decline requires a reason and sends it to the backend', async () => {
  vi.mocked(api.fetchDriverAssignmentRequests).mockResolvedValue([
    {
      requestId: 'request-8',
      tripId: 8,
      routeName: 'Tuyến B',
      vehiclePlate: '51B88888',
      tripCreatedAt: '2026-10-01T07:40:00Z',
      requestedAt: '2026-10-01T07:45:00Z',
    },
  ]);
  vi.mocked(api.declineDriverAssignmentRequest).mockResolvedValue({
    requestId: 'request-8',
    status: 'DECLINED',
    tripId: 8,
  });
  const wrapper = render(dialogStub);
  await flushPromises();
  await openNotifications(wrapper);
  await wrapper.find('.driver-assignment-requests .dispatch-secondary').trigger('click');
  await nextTick();
  const confirm = wrapper.find('.assignment-confirm');
  expect(confirm.exists()).toBe(true);
  const confirmButton = confirm.find('.danger-action');
  expect(confirmButton.attributes('disabled')).toBeDefined();
  await confirm.find('textarea').setValue('Không thể nhận chuyến');
  expect(wrapper.get('.assignment-confirm .danger-action').attributes('disabled')).toBeUndefined();
  await wrapper.get('.assignment-confirm .danger-action').trigger('click');
  await flushPromises();
  expect(api.declineDriverAssignmentRequest).toHaveBeenCalledWith(
    'request-8',
    'Không thể nhận chuyến',
  );
});

test('inbox remains usable when fetching direct requests fails', async () => {
  vi.mocked(api.fetchDriverAssignmentRequests).mockRejectedValue(new Error('Requests unavailable'));
  vi.mocked(api.fetchDispatchInbox).mockResolvedValue([
    {
      id: 5,
      type: 'DIRECT_ASSIGNMENT_REQUESTED',
      title: 'Yêu cầu nhận chuyến #7',
      detail: null,
      tripId: 7,
      offerId: null,
      createdAt: '2026-10-01T07:40:00Z',
      readAt: null,
    },
  ]);
  const wrapper = render();
  await flushPromises();
  expect(wrapper.get('.driver-notification-trigger').classes()).toContain('has-error');
  await openNotifications(wrapper);
  expect(wrapper.text()).toContain('Yêu cầu nhận chuyến #7');
  expect(wrapper.get('[role="alert"]').text()).toContain('Không thể tải yêu cầu nhận chuyến.');
  await wrapper.get('.driver-dispatch-inbox button').trigger('click');
  await flushPromises();
  expect(api.readDispatchInboxItem).toHaveBeenCalledWith(5);
});

test('driver inbox paginates notices and confirms deletion', async () => {
  vi.mocked(api.fetchDispatchInbox).mockResolvedValue(
    Array.from({ length: 11 }, (_, index) => ({
      ...notice,
      id: index + 1,
      title: `Thông báo ${index + 1}`,
    })),
  );
  const wrapper = render(dialogStub);
  await flushPromises();
  await openNotifications(wrapper);

  expect(wrapper.findAll('.driver-dispatch-inbox li')).toHaveLength(10);
  expect(wrapper.get('.driver-inbox-pagination').text()).toContain('Trang 1 / 2');
  await wrapper.get('.driver-inbox-pagination button:last-child').trigger('click');
  expect(wrapper.findAll('.driver-dispatch-inbox li')).toHaveLength(1);
  expect(wrapper.text()).toContain('Thông báo 11');
  await wrapper.get('.driver-dispatch-inbox .driver-inbox-delete').trigger('click');
  expect(wrapper.get('.assignment-confirm .danger-action').text()).toBe('Xác nhận xóa');
  await wrapper.get('.assignment-confirm .danger-action').trigger('click');
  await flushPromises();
  expect(api.deleteDispatchInboxItem).toHaveBeenCalledWith(11);
});

test('workspace has no scheduled ready controls and cleans up polling and requests', async () => {
  vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
  const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
  try {
    const wrapper = render();
    await flushPromises();
    expect(wrapper.find('.driver-dispatch-cards').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('Sẵn sàng');
    expect(wrapper.text()).not.toContain('Báo bận');
    vi.mocked(api.fetchDispatchInbox).mockResolvedValue([notice]);
    await vi.advanceTimersByTimeAsync(5000);
    await flushPromises();
    expect(api.fetchDispatchInbox).toHaveBeenCalledTimes(2);
    expect(wrapper.get('.driver-notifications-badge').text()).toBe('1');
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
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

test('badge does not double-count a pending request and reading never accepts it', async () => {
  vi.mocked(api.fetchDriverAssignmentRequests).mockResolvedValue([pending]);
  vi.mocked(api.fetchDispatchInbox).mockResolvedValue([notice]);
  const wrapper = render();
  await flushPromises();
  expect(wrapper.get('.driver-notifications-badge').text()).toBe('1');
  await openNotifications(wrapper);
  expect(wrapper.get('.driver-dispatch-heading').text()).toContain('1 chưa đọc · 1 yêu cầu chờ');
  vi.mocked(api.fetchDispatchInbox).mockResolvedValue([
    { ...notice, readAt: '2026-10-06T01:00:00Z' },
  ]);
  await wrapper.get('.driver-dispatch-inbox button').trigger('click');
  await flushPromises();
  expect(api.readDispatchInboxItem).toHaveBeenCalledWith(5);
  expect(api.acceptDriverAssignmentRequest).not.toHaveBeenCalled();
  expect(wrapper.get('.driver-dispatch-heading').text()).toContain('0 chưa đọc · 1 yêu cầu chờ');
  expect(wrapper.get('.driver-notifications-badge').text()).toBe('1');
  expect(wrapper.get('.driver-assignment-requests .dispatch-primary').text()).toBe('Nhận chuyến');
});

test('unrelated unread history and pending requests are counted independently', async () => {
  vi.mocked(api.fetchDriverAssignmentRequests).mockResolvedValue([pending]);
  vi.mocked(api.fetchDispatchInbox).mockResolvedValue([
    { ...notice, id: 8, assignmentRequestId: 'canceled-request' },
    { ...notice, id: 9, type: 'DIRECT_ASSIGNMENT_ACCEPTED', assignmentRequestId: 'request-7' },
    { ...notice, id: 10, readAt: '2026-10-06T01:00:00Z' },
  ]);
  const wrapper = render();
  await flushPromises();
  expect(wrapper.get('.driver-notifications-badge').text()).toBe('3');
});

test('panel handles keyboard, internal clicks and outside focus without stealing focus', async () => {
  const wrapper = render();
  await flushPromises();
  await openNotifications(wrapper);
  expect(document.activeElement).toBe(wrapper.get('[role="dialog"]').element);
  await wrapper.get('.driver-dispatch-heading').trigger('pointerdown');
  expect(wrapper.find('[role="dialog"]').exists()).toBe(true);
  document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
  await nextTick();
  expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
  expect(document.activeElement).toBe(wrapper.get('.driver-notification-trigger').element);
  await openNotifications(wrapper);
  const outside = document.createElement('button');
  document.body.append(outside);
  outside.focus();
  await nextTick();
  expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
  expect(document.activeElement).toBe(outside);
  await openNotifications(wrapper);
  document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }));
  await nextTick();
  expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
});

test('decline dialog owns Escape and outside interaction until it is dismissed', async () => {
  vi.mocked(api.fetchDriverAssignmentRequests).mockResolvedValue([pending]);
  const wrapper = render(dialogStub);
  await flushPromises();
  await openNotifications(wrapper);
  await wrapper.get('.driver-assignment-requests .dispatch-secondary').trigger('click');
  document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
  document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }));
  await nextTick();
  expect(wrapper.find('[role="dialog"]').exists()).toBe(true);
  expect(wrapper.find('.assignment-confirm').exists()).toBe(true);
  await wrapper.get('.assignment-confirm .cancel-action').trigger('click');
  expect(api.declineDriverAssignmentRequest).not.toHaveBeenCalled();
  document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
  await nextTick();
  expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
});

test('accept conflict refreshes stale requests and remains retryable inside the panel', async () => {
  vi.mocked(api.fetchDriverAssignmentRequests)
    .mockResolvedValueOnce([pending])
    .mockResolvedValue([]);
  vi.mocked(api.acceptDriverAssignmentRequest).mockRejectedValue(
    new api.DispatchApiError(409, 'ASSIGNMENT_REQUEST_NOT_PENDING', 'Yêu cầu đã bị hủy.'),
  );
  const wrapper = render();
  await flushPromises();
  await openNotifications(wrapper);
  await wrapper.get('.driver-assignment-requests .dispatch-primary').trigger('click');
  await flushPromises();
  expect(wrapper.get('[role="alert"]').text()).toContain(
    'Yêu cầu đã bị hủy. Đã tải lại trạng thái mới.',
  );
  expect(wrapper.find('.driver-assignment-requests').exists()).toBe(false);
  expect(wrapper.emitted('changed')).toBeUndefined();
  await wrapper.get('[aria-label="Làm mới thông báo"]').trigger('click');
  await flushPromises();
  expect(wrapper.find('[role="alert"]').exists()).toBe(false);
});

test('leaving the portal during an action never restarts requests or emits a trip refresh', async () => {
  vi.mocked(api.fetchDriverAssignmentRequests).mockResolvedValue([pending]);
  let resolveAction!: (value: AssignmentActionResponse) => void;
  vi.mocked(api.acceptDriverAssignmentRequest).mockReturnValue(
    new Promise((resolve) => {
      resolveAction = resolve;
    }),
  );
  const wrapper = render();
  await flushPromises();
  await openNotifications(wrapper);
  await wrapper.get('.driver-assignment-requests .dispatch-primary').trigger('click');
  expect(
    wrapper.get('.driver-assignment-requests .dispatch-primary').attributes('disabled'),
  ).toBeDefined();
  wrapper.unmount();
  wrappers.pop();
  resolveAction({ requestId: pending.requestId, status: 'ACCEPTED', tripId: pending.tripId });
  await flushPromises();
  expect(api.fetchDispatchInbox).toHaveBeenCalledTimes(1);
  expect(api.fetchDriverAssignmentRequests).toHaveBeenCalledTimes(1);
  expect(wrapper.emitted('changed')).toBeUndefined();
});

test('an action stays locked until its pending request list is synchronized', async () => {
  let resolveRefresh!: (value: DriverAssignmentRequest[]) => void;
  const pendingRefresh = new Promise<DriverAssignmentRequest[]>((resolve) => {
    resolveRefresh = resolve;
  });
  vi.mocked(api.fetchDriverAssignmentRequests)
    .mockResolvedValueOnce([pending])
    .mockReturnValueOnce(pendingRefresh);
  vi.mocked(api.acceptDriverAssignmentRequest).mockResolvedValue({
    requestId: pending.requestId,
    status: 'ACCEPTED',
    tripId: pending.tripId,
  });
  const wrapper = render();
  await flushPromises();
  await openNotifications(wrapper);
  await wrapper.get('.driver-assignment-requests .dispatch-primary').trigger('click');
  await flushPromises();
  expect(
    wrapper.get('.driver-assignment-requests .dispatch-primary').attributes('disabled'),
  ).toBeDefined();
  await wrapper.get('.driver-assignment-requests .dispatch-primary').trigger('click');
  expect(api.acceptDriverAssignmentRequest).toHaveBeenCalledTimes(1);
  resolveRefresh([]);
  await flushPromises();
  expect(wrapper.find('.driver-assignment-requests').exists()).toBe(false);
  expect(wrapper.get('[aria-label="Làm mới thông báo"]').attributes('disabled')).toBeUndefined();
});
