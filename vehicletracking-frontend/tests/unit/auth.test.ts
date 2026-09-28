import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { defineComponent, nextTick } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';
import { flushPromises, mount } from '@vue/test-utils';
import { createAuthState } from '@/features/auth/composables/authState';
import { authKey } from '@/features/auth/composables/useAuth';
import { installAuthGuards } from '@/app/router/guards';
import { fetchCurrentUser, login, logout, registerAdmin } from '@/features/auth/api/auth';
import type { AuthUser } from '@/features/auth/types/auth';
import LoginPage from '../../src/pages/LoginPage.vue';
import AdminRegistrationPage from '../../src/pages/AdminRegistrationPage.vue';
import SidePanel from '@/shared/components/SidePanel.vue';
import { notifyError, notifySuccess } from '@/shared/notifications/toast';

vi.mock('@/features/auth/api/auth', () => ({ fetchCurrentUser: vi.fn(), login: vi.fn(), logout: vi.fn(), registerAdmin: vi.fn() }));
vi.mock('@/shared/notifications/toast', () => ({
  notifyError: vi.fn(),
  notifySuccess: vi.fn(),
}));
const admin: AuthUser = { accountId: 1, username: 'admin.fixture', role: 'ADMIN', active: true, driverId: null, driverName: null };
const driver: AuthUser = { ...admin, role: 'DRIVER', driverId: 1, driverName: 'Fixture driver' };
const Page = defineComponent({ template: '<div />' });
const makeRouter = () => createRouter({ history: createMemoryHistory(), linkActiveClass: '', linkExactActiveClass: '', routes: [
  { path: '/login', component: LoginPage, meta: { guestOnly: true } }, { path: '/register', component: AdminRegistrationPage, meta: { guestOnly: true } },
  { path: '/dashboard', component: Page, meta: { role: 'ADMIN' } }, { path: '/reports', component: Page, meta: { role: 'ADMIN' } },
  { path: '/driver/today', component: Page, meta: { role: 'DRIVER' } },
] });
beforeEach(() => { vi.resetAllMocks(); window.history.replaceState({}, ''); });
afterEach(() => { document.body.innerHTML = ''; });

test('session bootstrap is single-flight and a late bootstrap cannot overwrite login', async () => {
  let resolve!: (user: AuthUser) => void;
  vi.mocked(fetchCurrentUser).mockReturnValue(new Promise(r => { resolve = r; }));
  vi.mocked(login).mockResolvedValue(admin);
  const auth = createAuthState();
  const first = auth.initialize(); expect(auth.initialize()).toBe(first);
  await auth.login({ username: 'admin.fixture', password: 'fixture-only' });
  resolve(driver); await first;
  expect(auth.user).toEqual(admin); expect(auth.loading).toBe(false);
  expect(fetchCurrentUser).toHaveBeenCalledTimes(1);
});
test.each([['guest', null, '/reports', '/login'], ['driver', driver, '/reports', '/driver/today'], ['admin', admin, '/driver/today', '/dashboard'], ['signed-in', admin, '/register', '/dashboard']] as const)('%s role redirect preserves boundary', async (_label, user, from, to) => {
  vi.mocked(fetchCurrentUser).mockResolvedValue(user as AuthUser);
  const auth = createAuthState(), router = makeRouter();
  const dispose = installAuthGuards(router, auth);
  await router.push(from); expect(router.currentRoute.value.path).toBe(to);
  if (!user) expect(router.options.history.state.from).toBe('/reports');
  dispose();
});

test('logout clears protected session and redirects even when backend fails', async () => {
  vi.mocked(fetchCurrentUser).mockResolvedValue(admin); vi.mocked(logout).mockRejectedValue(new Error('offline'));
  const auth = createAuthState(), router = makeRouter(), dispose = installAuthGuards(router, auth);
  await router.push('/reports'); await expect(auth.logout()).rejects.toThrow('offline');
  await flushPromises(); expect(auth.user).toBeNull(); expect(router.currentRoute.value.path).toBe('/login'); dispose();
});

test('login trims username, retains password and returns admin to requested route', async () => {
  vi.mocked(fetchCurrentUser).mockResolvedValue(null as unknown as AuthUser); vi.mocked(login).mockResolvedValue(admin);
  const auth = createAuthState(), router = makeRouter(); await router.push('/login');
  window.history.replaceState({ from: '/reports' }, '');
  const wrapper = mount(LoginPage, { global: { plugins: [router], provide: { [authKey as symbol]: auth } } });
  await wrapper.find('input[autocomplete=username]').setValue('  admin.fixture  ');
  await wrapper.find('input[type=password]').setValue('  fixture-password  ');
  await wrapper.find('form').trigger('submit'); await flushPromises();
  expect(login).toHaveBeenCalledWith({ username: 'admin.fixture', password: '  fixture-password  ' });
  expect(router.currentRoute.value.path).toBe('/reports'); wrapper.unmount();
});

test('registration mismatch and success use toast notifications', async () => {
  const router = makeRouter(); await router.push('/register');
  const wrapper = mount(AdminRegistrationPage, { global: { plugins: [router] } });
  const inputs = wrapper.findAll('input');
  expect(inputs[1].attributes('minlength')).toBe('8');
  expect(inputs[2].attributes('minlength')).toBe('8');
  await inputs[0].setValue(' New.Admin '); await inputs[1].setValue('pass1234'); await inputs[2].setValue('different');
  await wrapper.find('form').trigger('submit'); expect(registerAdmin).not.toHaveBeenCalled();
  expect(notifyError).toHaveBeenCalledWith('Mật khẩu xác nhận không khớp.');
  await inputs[2].setValue('pass1234'); await wrapper.find('form').trigger('submit'); await flushPromises();
  expect(registerAdmin).toHaveBeenCalledWith({ username: 'New.Admin', password: 'pass1234' });
  expect(notifySuccess).toHaveBeenCalledWith(
    'Đã tạo tài khoản new.admin. Bạn có thể đăng nhập ngay.',
  );
  expect(router.currentRoute.value.path).toBe('/login'); wrapper.unmount();
});

test('side panel requests native modal, blocks busy Escape and restores opener', async () => {
  const opener = document.createElement('button'); document.body.append(opener); opener.focus();
  // jsdom does not implement the browser modal top layer. This verifies lifecycle,
  // not actual focus trapping; that remains a browser-suite assertion.
  const originalShow = Object.getOwnPropertyDescriptor(HTMLDialogElement.prototype, 'showModal');
  const originalClose = Object.getOwnPropertyDescriptor(HTMLDialogElement.prototype, 'close');
  const show = vi.fn(function (this: HTMLDialogElement) { this.open = true; });
  const close = vi.fn(function (this: HTMLDialogElement) { this.open = false; });
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', { configurable: true, value: show });
  Object.defineProperty(HTMLDialogElement.prototype, 'close', { configurable: true, value: close });
  const onClose = vi.fn(); const wrapper = mount(SidePanel, { attachTo: document.body, props: { className: 'fixture', label: 'Fixture dialog', busy: true, contentSized: true, onClose }, slots: { default: '<button>Inside</button>' } });
  expect(wrapper.get('dialog').classes()).toContain('is-content-sized');
  expect(show).toHaveBeenCalledOnce(); await wrapper.get('dialog').trigger('cancel'); expect(onClose).not.toHaveBeenCalled();
  await wrapper.setProps({ busy: false }); await wrapper.get('dialog').trigger('cancel'); expect(onClose).toHaveBeenCalledOnce();
  wrapper.unmount(); await nextTick(); expect(close).toHaveBeenCalledOnce(); expect(document.activeElement).toBe(opener);
  if (originalShow) Object.defineProperty(HTMLDialogElement.prototype, 'showModal', originalShow); else Reflect.deleteProperty(HTMLDialogElement.prototype, 'showModal');
  if (originalClose) Object.defineProperty(HTMLDialogElement.prototype, 'close', originalClose); else Reflect.deleteProperty(HTMLDialogElement.prototype, 'close');
});
