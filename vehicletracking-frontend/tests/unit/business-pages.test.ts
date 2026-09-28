import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import ReportsPage from '../../src/pages/ReportsPage.vue';
import UserManagementPage from '../../src/pages/UserManagementPage.vue';
import ScheduleManagementPage from '../../src/pages/ScheduleManagementPage.vue';
import FleetConfirmDialog from '@/features/fleet/components/FleetConfirmDialog.vue';
import { fetchDrivers, fetchFleetVehicles } from '@/features/fleet/api/fleet';
import { fetchRoutes } from '@/features/routes/api/routes';
import { fetchOperationalReport } from '@/features/reports/api/reports';
import {
  createDriverAccount,
  fetchUserAccounts,
  resetDriverPassword,
  setUserAccountActive,
  type DriverAccountCreated,
  type UserAccount,
} from '@/features/auth/api/users';
import {
  createSchedule,
  fetchSchedules,
  setScheduleEnabled,
} from '@/features/schedules/api/schedules';
import type { Driver, FleetVehicle } from '@/features/fleet/types/fleet';
import type { OperationalReport } from '@/features/reports/types/reports';
import type { TripSchedule } from '@/features/schedules/types/schedule';
import type { RouteSummary } from '@/features/routes/types/route';
import { notifyError, notifySuccess } from '@/shared/notifications/toast';

vi.mock('@/features/fleet/api/fleet', () => ({
  fetchDrivers: vi.fn(),
  fetchFleetVehicles: vi.fn(),
}));
vi.mock('@/features/routes/api/routes', () => ({ fetchRoutes: vi.fn() }));
vi.mock('@/features/reports/api/reports', () => ({ fetchOperationalReport: vi.fn() }));
vi.mock('@/features/auth/api/users', () => ({
  createDriverAccount: vi.fn(),
  fetchUserAccounts: vi.fn(),
  resetDriverPassword: vi.fn(),
  setUserAccountActive: vi.fn(),
}));
vi.mock('@/features/schedules/api/schedules', () => ({
  createSchedule: vi.fn(),
  fetchSchedules: vi.fn(),
  setScheduleEnabled: vi.fn(),
  updateSchedule: vi.fn(),
}));
vi.mock('@/shared/notifications/toast', () => ({
  notifyError: vi.fn(),
  notifySuccess: vi.fn(),
}));
const stamp = '2026-09-23T01:00:00Z';
const driver: Driver = {
  id: 1,
  fullName: 'Available driver',
  phoneNumber: '0901234567',
  licenseNumber: 'B2-FIXTURE',
  active: true,
  createdAt: stamp,
  updatedAt: stamp,
};
const vehicle: FleetVehicle = {
  id: 1,
  plateNumber: '51B12345',
  name: 'Fixture car',
  description: null,
  vehicleType: 'CAR',
  active: true,
  driver,
  createdAt: stamp,
  updatedAt: stamp,
};
const route: RouteSummary = {
  id: 1,
  name: 'Fixture route',
  transportMode: 'CAR',
  routingProvider: 'HERE',
  startStationName: 'First',
  endStationName: 'Last',
  stopCount: 2,
  totalDistanceMeters: 1000,
  estimatedTravelDurationSeconds: 300,
  totalDwellDurationSeconds: 0,
  estimatedTripDurationSeconds: 300,
  calculatedAt: stamp,
  createdAt: stamp,
  active: true,
};
const report: OperationalReport = {
  from: '2026-09-01',
  to: '2026-09-23',
  generatedAt: stamp,
  tripCount: 142,
  completedTripCount: 124,
  totalDistanceMeters: 1000,
  totalRunningSeconds: 3600,
  onTimeRatePercent: 95,
  lateTripCount: 3,
  offRouteEventCount: 2,
  overspeedEventCount: 1,
  speedLimitKmh: 60,
};
const account: UserAccount = {
  id: 2,
  username: 'linked.driver',
  role: 'DRIVER',
  active: true,
  driverId: 2,
  driverName: 'Linked driver',
  driverLicenseNumber: 'B2-LINKED',
};
const schedule: TripSchedule = {
  id: 1,
  name: 'Morning',
  routeId: 1,
  routeName: route.name,
  vehicleId: 1,
  vehiclePlate: vehicle.plateNumber,
  driverId: 1,
  driverName: driver.fullName,
  frequency: 'WEEKLY',
  scheduledDate: null,
  weekdaysMask: 31,
  departureTime: '08:00:00',
  timezone: 'Asia/Ho_Chi_Minh',
  effectiveFrom: '2026-09-01',
  effectiveUntil: null,
  enabled: true,
  nextRunAt: stamp,
  lastRunAt: null,
  lastRunStatus: null,
  lastRunMessage: null,
};
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((r) => {
    resolve = r;
  });
  return { promise, resolve };
}
const cleanups: (() => void)[] = [];
const originalShow = Object.getOwnPropertyDescriptor(HTMLDialogElement.prototype, 'showModal'),
  originalClose = Object.getOwnPropertyDescriptor(HTMLDialogElement.prototype, 'close');
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(fetchDrivers).mockResolvedValue([
    driver,
    { ...driver, id: 2, fullName: 'Linked driver' },
    { ...driver, id: 3, active: false },
  ]);
  vi.mocked(fetchFleetVehicles).mockResolvedValue([vehicle]);
  vi.mocked(fetchRoutes).mockResolvedValue([route]);
  vi.mocked(fetchOperationalReport).mockResolvedValue(report);
  vi.mocked(fetchUserAccounts).mockResolvedValue([account]);
  vi.mocked(fetchSchedules).mockResolvedValue([schedule]);
  // jsdom lifecycle only; native focus trapping still needs the browser suite.
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', {
    configurable: true,
    value: function (this: HTMLDialogElement) {
      this.open = true;
    },
  });
  Object.defineProperty(HTMLDialogElement.prototype, 'close', {
    configurable: true,
    value: function (this: HTMLDialogElement) {
      this.open = false;
    },
  });
});
afterEach(() => {
  cleanups.splice(0).forEach((dispose) => dispose());
  for (const [key, descriptor] of [
    ['showModal', originalShow],
    ['close', originalClose],
  ] as const) {
    if (descriptor) Object.defineProperty(HTMLDialogElement.prototype, key, descriptor);
    else Reflect.deleteProperty(HTMLDialogElement.prototype, key);
  }
});

test('reports keep numeric filters, abort stale loads and do not replace a newer result', async () => {
  const wrapper = mount(ReportsPage);
  cleanups.push(() => wrapper.unmount());
  await flushPromises();
  const old = deferred<OperationalReport>();
  vi.mocked(fetchOperationalReport).mockReturnValueOnce(old.promise);
  await wrapper.findAll('select')[0].setValue('1');
  expect(fetchOperationalReport).toHaveBeenLastCalledWith(
    expect.objectContaining({ vehicleId: 1 }),
    expect.any(AbortSignal),
  );
  const signal = vi.mocked(fetchOperationalReport).mock.calls[1][1]!;
  vi.mocked(fetchOperationalReport).mockResolvedValueOnce({ ...report, tripCount: 9 });
  await wrapper.findAll('select')[1].setValue('2');
  await flushPromises();
  expect(signal.aborted).toBe(true);
  old.resolve({ ...report, tripCount: 999 });
  await flushPromises();
  expect(wrapper.findAll('.reports-metric strong')[0].text()).toBe('9');
  expect(fetchOperationalReport).toHaveBeenLastCalledWith(
    expect.objectContaining({ vehicleId: 1, driverId: 2 }),
    expect.any(AbortSignal),
  );
  const lastSignal = vi.mocked(fetchOperationalReport).mock.calls[2][1]!;
  wrapper.unmount();
  expect(lastSignal.aborted).toBe(true);
});

test('reports reject incomplete dates without HTTP and can reset/retry an API failure', async () => {
  const wrapper = mount(ReportsPage);
  cleanups.push(() => wrapper.unmount());
  await flushPromises();
  await wrapper.findAll('input[type=date]')[0].setValue('');
  await flushPromises();
  expect(fetchOperationalReport).toHaveBeenCalledTimes(1);
  expect(notifyError).toHaveBeenCalledWith('Hãy chọn đầy đủ ngày bắt đầu và ngày kết thúc.');
  vi.mocked(fetchOperationalReport).mockRejectedValueOnce(new Error('Fixture unavailable'));
  await wrapper.get('.reports-reset').trigger('click');
  await flushPromises();
  expect(notifyError).toHaveBeenCalledWith('Fixture unavailable');
  await wrapper.get('.reports-refresh').trigger('click');
  await flushPromises();
  expect(wrapper.findAll('.reports-metric')).toHaveLength(7);
});

test('user creation only selects an available driver and shows generated credentials', async () => {
  const wrapper = mount(UserManagementPage);
  cleanups.push(() => wrapper.unmount());
  await flushPromises();
  expect(wrapper.find('.user-account-modal').exists()).toBe(false);
  await wrapper.get('.business-button.primary').trigger('click');
  expect(wrapper.get('.user-account-modal').attributes('open')).toBeDefined();
  expect(wrapper.findAll('.user-account-modal input')).toHaveLength(0);
  expect(wrapper.findAll('select option').map((option) => option.attributes('value'))).toEqual([
    '',
    '1',
  ]);
  const pending = deferred<DriverAccountCreated>();
  vi.mocked(createDriverAccount).mockReturnValueOnce(pending.promise);
  await wrapper.get('select').setValue('1');
  await wrapper.get('form').trigger('submit');
  await wrapper.get('form').trigger('submit');
  expect(createDriverAccount).toHaveBeenCalledTimes(1);
  expect(createDriverAccount).toHaveBeenCalledWith({ driverId: 1 });
  expect(wrapper.get('form button').attributes('disabled')).toBeDefined();
  pending.resolve({
    ...account,
    id: 3,
    username: 'drivera',
    driverId: 1,
    driverName: driver.fullName,
    temporaryPassword: 'Tmp8Pass',
  });
  await flushPromises();
  expect(notifySuccess).toHaveBeenCalledWith('Đã cấp tài khoản drivera.');
  expect(wrapper.get('.user-issued-account').text()).toContain('drivera');
  expect(wrapper.get('.user-issued-account').text()).toContain('Tmp8Pass');
  expect(wrapper.get('.user-account-modal').attributes('open')).toBeDefined();
  expect(wrapper.get('.business-button.primary').attributes('disabled')).toBeDefined();
  await wrapper.get('.user-issued-done').trigger('click');
  expect(wrapper.find('.user-account-modal').exists()).toBe(false);
});

test('user page visually separates roles and locked access states', async () => {
  const adminAccount: UserAccount = {
    ...account,
    id: 1,
    username: 'admin',
    role: 'ADMIN',
    driverId: null,
    driverName: null,
    driverLicenseNumber: null,
  };
  const lockedAccount: UserAccount = {
    ...account,
    id: 3,
    username: 'locked.driver',
    active: false,
    driverId: 3,
  };
  vi.mocked(fetchUserAccounts).mockResolvedValueOnce([adminAccount, account, lockedAccount]);
  const wrapper = mount(UserManagementPage);
  cleanups.push(() => wrapper.unmount());
  await flushPromises();

  expect(wrapper.get('.users-overview-card.admin strong').text()).toBe('1');
  expect(wrapper.get('.users-overview-card.driver strong').text()).toBe('2');
  expect(wrapper.get('.users-overview-card.locked strong').text()).toBe('1');
  expect(wrapper.get('.user-role.admin').text()).toContain('Quản trị viên');
  expect(wrapper.findAll('.user-role.driver')).toHaveLength(2);
  expect(wrapper.get('.user-row.is-locked').attributes('data-active')).toBe('false');
  expect(wrapper.get('.user-row.is-locked .user-inactive').text()).toContain('Tài khoản bị khóa');
  expect(wrapper.get('.user-row.is-locked .user-account-action.unlock').text()).toContain(
    'Mở khóa',
  );
});

test('admin resets a driver password through a guarded modal', async () => {
  const wrapper = mount(UserManagementPage);
  cleanups.push(() => wrapper.unmount());
  await flushPromises();

  await wrapper.get('[aria-label="Đặt lại mật khẩu tài khoản linked.driver"]').trigger('click');
  expect(wrapper.get('.user-password-reset-modal').attributes('open')).toBeDefined();
  expect(wrapper.get('.user-password-target').text()).toContain('Linked driver');

  const inputs = wrapper.findAll('.user-password-reset input');
  await inputs[0].setValue('pass1234');
  await inputs[1].setValue('different-password');
  await wrapper.get('.user-password-reset form').trigger('submit');
  await flushPromises();
  expect(resetDriverPassword).not.toHaveBeenCalled();
  expect(notifyError).toHaveBeenCalledWith('Mật khẩu xác nhận không khớp.');

  await inputs[1].setValue('pass1234');
  const pending = deferred<void>();
  vi.mocked(resetDriverPassword).mockReturnValueOnce(pending.promise);
  await wrapper.get('.user-password-reset form').trigger('submit');
  await wrapper.get('.user-password-reset form').trigger('submit');
  expect(resetDriverPassword).toHaveBeenCalledTimes(1);
  expect(resetDriverPassword).toHaveBeenCalledWith(2, { password: 'pass1234' });
  expect(wrapper.get('.user-password-reset form button').attributes('disabled')).toBeDefined();

  pending.resolve();
  await flushPromises();
  expect(notifySuccess).toHaveBeenCalledWith('Đã đặt lại mật khẩu cho tài khoản linked.driver.');
  expect(wrapper.find('.user-password-reset-modal').exists()).toBe(false);
});

test('user page preserves toggle error/retry and aborts outstanding reads on unmount', async () => {
  const wrapper = mount(UserManagementPage);
  cleanups.push(() => wrapper.unmount());
  await flushPromises();
  vi.mocked(setUserAccountActive).mockRejectedValueOnce(new Error('Fixture conflict'));
  await wrapper.get('.user-row .user-account-action.lock').trigger('click');
  await flushPromises();
  expect(setUserAccountActive).toHaveBeenCalledWith(2, false);
  expect(notifyError).toHaveBeenCalledWith('Fixture conflict');
  await wrapper.get('.users-refresh').trigger('click');
  await flushPromises();
  vi.mocked(setUserAccountActive).mockResolvedValueOnce({ ...account, active: false });
  await wrapper.get('.user-row .user-account-action.lock').trigger('click');
  await flushPromises();
  expect(wrapper.get('.user-inactive').text()).toContain('Tài khoản bị khóa');
  const signal = vi.mocked(fetchUserAccounts).mock.calls[1][0]!;
  wrapper.unmount();
  expect(signal.aborted).toBe(true);
});

test('schedule form validates weekdays and sends ONCE payload without weekly mask or coerced timezone', async () => {
  const wrapper = mount(ScheduleManagementPage, { global: { stubs: { RouterLink: true } } });
  cleanups.push(() => wrapper.unmount());
  await flushPromises();
  await wrapper.get('.business-button.primary').trigger('click');
  const form = wrapper.get('form');
  await form.get('input[maxlength="150"]').setValue(' One time ');
  for (const select of form.findAll('select')) await select.setValue('1');
  await form.findAll('input[type=date]')[0].setValue('2026-09-23');
  for (const checkbox of form.findAll('input[type=checkbox]').slice(0, 5))
    await checkbox.setValue(false);
  await form.trigger('submit');
  expect(createSchedule).not.toHaveBeenCalled();
  expect(form.get('[role=alert]').text()).toContain('ít nhất một ngày');
  await form.findAll('input[type=radio]')[0].setValue(true);
  await form.trigger('submit');
  expect(form.get('[role=alert]').text()).toContain('ngày chạy cụ thể');
  await form.get('fieldset input[type=date]').setValue('2026-09-24');
  const pending = deferred<TripSchedule>();
  vi.mocked(createSchedule).mockReturnValueOnce(pending.promise);
  await form.trigger('submit');
  await form.trigger('submit');
  expect(createSchedule).toHaveBeenCalledTimes(1);
  expect(createSchedule).toHaveBeenCalledWith({
    name: 'One time',
    routeId: 1,
    vehicleId: 1,
    driverId: 1,
    frequency: 'ONCE',
    scheduledDate: '2026-09-24',
    weekdaysMask: 0,
    departureTime: '08:00',
    timezone: 'Asia/Ho_Chi_Minh',
    effectiveFrom: '2026-09-23',
    effectiveUntil: null,
  });
  await wrapper.get('dialog').trigger('cancel');
  expect(wrapper.find('dialog').exists()).toBe(true);
  pending.resolve({ ...schedule, id: 2, name: 'One time' });
  await flushPromises();
  expect(wrapper.find('dialog').exists()).toBe(false);
  expect(wrapper.findAll('.schedule-card')).toHaveLength(2);
});

test('schedule toggle requires confirmation, retains conflict for retry and updates status', async () => {
  const wrapper = mount(ScheduleManagementPage, { global: { stubs: { RouterLink: true } } });
  cleanups.push(() => wrapper.unmount());
  await flushPromises();
  await wrapper.get('[aria-label="Tạm dừng lịch Morning"]').trigger('click');
  expect(setScheduleEnabled).not.toHaveBeenCalled();
  vi.mocked(setScheduleEnabled).mockRejectedValueOnce(new Error('Fixture conflict'));
  await wrapper.get('.schedule-confirm .schedule-button-danger').trigger('click');
  await flushPromises();
  expect(wrapper.find('.schedule-confirm').exists()).toBe(true);
  vi.mocked(setScheduleEnabled).mockResolvedValueOnce({ ...schedule, enabled: false });
  await wrapper.get('.schedule-confirm .schedule-button-danger').trigger('click');
  await flushPromises();
  expect(setScheduleEnabled).toHaveBeenLastCalledWith(1, false);
  expect(wrapper.find('dialog').exists()).toBe(false);
  expect(wrapper.get('.schedule-card').attributes('data-enabled')).toBe('false');
});

test('fleet confirmation keeps slot, busy Escape and disabled-confirm semantics', async () => {
  const onClose = vi.fn(),
    onConfirm = vi.fn();
  const nativeClose = vi.spyOn(HTMLDialogElement.prototype, 'close');
  const wrapper = mount(FleetConfirmDialog, {
    props: {
      title: 'Fixture',
      message: 'Confirm action',
      confirmLabel: 'Confirm',
      busy: true,
      onClose,
      onConfirm,
    },
    slots: { default: '<input aria-label="Reason" />' },
  });
  cleanups.push(() => {
    wrapper.unmount();
    nativeClose.mockRestore();
  });
  expect((wrapper.get('dialog').element as HTMLDialogElement).open).toBe(true);
  expect(wrapper.find('input[aria-label=Reason]').exists()).toBe(true);
  await wrapper.get('dialog').trigger('cancel');
  expect(onClose).not.toHaveBeenCalled();
  expect(wrapper.get('.danger-action').attributes('disabled')).toBeDefined();
  await wrapper.setProps({ busy: false, confirmDisabled: true });
  expect(wrapper.get('.danger-action').attributes('disabled')).toBeDefined();
  await wrapper.setProps({ confirmDisabled: false });
  await wrapper.get('.danger-action').trigger('click');
  expect(onConfirm).toHaveBeenCalledTimes(1);
  await wrapper.get('dialog').trigger('cancel');
  expect(onClose).toHaveBeenCalledTimes(1);
  wrapper.unmount();
  expect(nativeClose).toHaveBeenCalledTimes(1);
});
