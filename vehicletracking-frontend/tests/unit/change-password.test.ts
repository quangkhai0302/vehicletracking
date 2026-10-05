import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import { defineComponent } from 'vue';
import ChangePasswordPage from '@/pages/ChangePasswordPage.vue';
import { authKey } from '@/features/auth/composables/useAuth';
import { createAuthState } from '@/features/auth/composables/authState';
import { changePassword, fetchCurrentUser, logout } from '@/features/auth/api/auth';
import type { AuthUser } from '@/features/auth/types/auth';
import { notifyError, notifySuccess } from '@/shared/notifications/toast';

vi.mock('@/features/auth/api/auth', () => ({
  changePassword: vi.fn(),
  fetchCurrentUser: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  registerAdmin: vi.fn(),
}));
vi.mock('@/shared/notifications/toast', () => ({ notifyError: vi.fn(), notifySuccess: vi.fn() }));
const driver: AuthUser = {
  accountId: 2,
  username: 'driver.fixture',
  role: 'DRIVER',
  active: true,
  passwordChangeRequired: true,
  driverId: 1,
  driverName: 'Tài xế thử nghiệm',
};
const cleanups: (() => void)[] = [];
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(fetchCurrentUser).mockResolvedValue(driver);
  vi.mocked(changePassword).mockResolvedValue(undefined);
});
afterEach(() => cleanups.splice(0).forEach((dispose) => dispose()));
async function setup(required = true) {
  const auth = createAuthState();
  await auth.initialize();
  auth.user = { ...driver, passwordChangeRequired: required };
  const Empty = defineComponent({ template: '<div />' });
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/driver/change-password', component: ChangePasswordPage },
      { path: '/driver/today', component: Empty },
      { path: '/login', component: Empty },
    ],
  });
  await router.push('/driver/change-password');
  const wrapper = mount(ChangePasswordPage, {
    global: { plugins: [router], provide: { [authKey as symbol]: auth } },
  });
  cleanups.push(() => wrapper.unmount());
  const fill = async (current = 'current-password', next = 'new-password', confirm = next) => {
    const inputs = wrapper.findAll('input');
    await inputs[0].setValue(current);
    await inputs[1].setValue(next);
    await inputs[2].setValue(confirm);
  };
  return { wrapper, auth, router, fill };
}

test('required form has no skip and voluntary form returns to the portal', async () => {
  const forced = await setup();
  expect(forced.wrapper.text()).toContain('trước khi xem chuyến đi và lịch chạy');
  expect(
    forced.wrapper.findAll('.auth-password-actions button').map((button) => button.text()),
  ).toEqual(['Đăng xuất']);
  expect(forced.wrapper.get('input').attributes('autocomplete')).toBe('current-password');
  expect(forced.wrapper.findAll('input[autocomplete="new-password"]')).toHaveLength(2);
  const voluntary = await setup(false);
  await voluntary.wrapper.get('.auth-password-actions button').trigger('click');
  await flushPromises();
  expect(voluntary.router.currentRoute.value.path).toBe('/driver/today');
});

test.each([
  ['', 'new-password', 'new-password', 'Hãy nhập mật khẩu hiện tại.'],
  ['current-password', 'short', 'short', 'Mật khẩu mới phải dài từ 8 đến 100 ký tự.'],
  ['current-password', '😀'.repeat(4), '😀'.repeat(4), 'Mật khẩu mới phải dài từ 8 đến 100 ký tự.'],
  [
    'current-password',
    'a'.repeat(101),
    'a'.repeat(101),
    'Mật khẩu mới phải dài từ 8 đến 100 ký tự.',
  ],
  [
    'current-password',
    '🔑'.repeat(19),
    '🔑'.repeat(19),
    'Mật khẩu quá dài. Hãy dùng mật khẩu ngắn hơn, nhất là khi có ký tự có dấu.',
  ],
  ['current-password', 'new-password', 'wrong-confirmation', 'Mật khẩu xác nhận không khớp.'],
  [
    'current-password',
    'current-password',
    'current-password',
    'Mật khẩu mới phải khác mật khẩu hiện tại.',
  ],
])(
  'invalid passwords are rejected without API calls (%s)',
  async (current, next, confirmation, message) => {
    const { wrapper, fill } = await setup();
    await fill(current, next, confirmation);
    await wrapper.get('form').trigger('submit');
    expect(changePassword).not.toHaveBeenCalled();
    expect(notifyError).toHaveBeenCalledWith(message);
  },
);

test('pending submit disables all actions, prevents duplicate requests, clears secrets and redirects on success', async () => {
  let finish!: () => void;
  vi.mocked(changePassword).mockReturnValueOnce(
    new Promise<void>((resolve) => {
      finish = resolve;
    }),
  );
  const { wrapper, auth, router, fill } = await setup();
  await fill(' current-password ', ' new-password ', ' new-password ');
  await wrapper.get('form').trigger('submit');
  await wrapper.get('form').trigger('submit');
  expect(changePassword).toHaveBeenCalledTimes(1);
  expect(changePassword).toHaveBeenCalledWith({
    currentPassword: ' current-password ',
    newPassword: ' new-password ',
    confirmPassword: ' new-password ',
  });
  for (const control of wrapper.findAll('input, button'))
    expect(control.attributes('disabled')).toBeDefined();
  finish();
  await flushPromises();
  expect(auth.user).toBeNull();
  expect(
    wrapper.findAll('input').every((input) => (input.element as HTMLInputElement).value === ''),
  ).toBe(true);
  expect(router.currentRoute.value.path).toBe('/login');
  expect(notifySuccess).toHaveBeenCalledWith(
    'Đã đổi mật khẩu. Vui lòng đăng nhập lại bằng mật khẩu mới.',
  );
  expect(logout).not.toHaveBeenCalled();
});

test('72 UTF-8 bytes is accepted and a server validation error keeps the form available', async () => {
  vi.mocked(changePassword).mockRejectedValueOnce(new Error('Mật khẩu hiện tại không đúng.'));
  const { wrapper, auth, router, fill } = await setup();
  await fill('current-password', '🔑'.repeat(18));
  await wrapper.get('form').trigger('submit');
  await flushPromises();
  expect(changePassword).toHaveBeenCalledOnce();
  expect(notifyError).toHaveBeenCalledWith('Mật khẩu hiện tại không đúng.');
  expect(auth.user?.passwordChangeRequired).toBe(true);
  expect(router.currentRoute.value.path).toBe('/driver/change-password');
  expect(wrapper.get('form button').attributes('disabled')).toBeUndefined();
});

test('eight Unicode code points are accepted independently of UTF-16 code units', async () => {
  const { wrapper, auth, router, fill } = await setup();
  const next = '😀'.repeat(8);
  await fill('current-password', next);
  await wrapper.get('form').trigger('submit');
  await flushPromises();
  expect(changePassword).toHaveBeenCalledWith({ currentPassword: 'current-password', newPassword: next, confirmPassword: next });
  expect(notifyError).not.toHaveBeenCalled();
  expect(auth.user).toBeNull();
  expect(router.currentRoute.value.path).toBe('/login');
});

test('logout clears passwords and returns to login even if the server is unavailable', async () => {
  vi.mocked(logout).mockRejectedValueOnce(new Error('Không thể kết nối máy chủ.'));
  const { wrapper, auth, router, fill } = await setup();
  await fill();
  await wrapper.get('.auth-password-actions button').trigger('click');
  await flushPromises();
  expect(auth.user).toBeNull();
  expect(
    wrapper.findAll('input').every((input) => (input.element as HTMLInputElement).value === ''),
  ).toBe(true);
  expect(router.currentRoute.value.path).toBe('/login');
  expect(notifyError).toHaveBeenCalledWith('Không thể kết nối máy chủ.');
});
