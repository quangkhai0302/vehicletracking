import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory } from 'vue-router';
import App from '../../src/App.vue';
import { createApplicationRouter } from '@/app/router';
import { createAuthState } from '@/features/auth/composables/authState';
import { authKey } from '@/features/auth/composables/useAuth';
import { installAuthGuards } from '@/app/router/guards';
import { fetchCurrentUser } from '@/features/auth/api/auth';
import type { AuthUser } from '@/features/auth/types/auth';

vi.mock('@/features/auth/api/auth', () => ({ fetchCurrentUser: vi.fn(), login: vi.fn(), logout: vi.fn(), registerAdmin: vi.fn() }));
const lifecycle = vi.hoisted(() => ({ mounted: vi.fn(), unmounted: vi.fn() }));
vi.mock('@/features/map/components/MapComponent.vue', async () => {
  const { defineComponent, h, onMounted, onUnmounted } = await import('vue');
  return { __esModule: true, default: defineComponent({ name: 'MapFixture', props: ['initialWorkspace', 'initialTripId', 'onWorkspaceChange'], setup(props) {
    onMounted(lifecycle.mounted); onUnmounted(lifecycle.unmounted);
    return () => h('div', { 'data-workspace': props.initialWorkspace, 'data-trip': props.initialTripId }, [h('button', { onClick: () => props.onWorkspaceChange('simulation') }, 'Simulate')]);
  } }) };
});
vi.mock('@/features/fleet/components/FleetWorkspace.vue', async () => {
  const { defineComponent, h, getCurrentInstance } = await import('vue');
  return { default: defineComponent({ props: ['initialTab', 'initialVehicleFilter'], setup(props) {
    const instance = getCurrentInstance()!.uid;
    return () => h('div', { 'data-fleet': props.initialTab, 'data-filter': props.initialVehicleFilter, 'data-instance': instance });
  } }) };
});
const admin: AuthUser = { accountId: 1, username: 'admin.fixture', role: 'ADMIN', active: true, driverId: null, driverName: null };
const cleanups: (() => void)[] = [];
beforeEach(() => { vi.resetAllMocks(); vi.mocked(fetchCurrentUser).mockResolvedValue(admin); });
afterEach(() => { cleanups.splice(0).forEach(dispose => dispose()); });

test('application router covers all fifteen URLs, root redirect and the protected catch-all', () => {
  const router = createApplicationRouter(createMemoryHistory());
  const adminPaths = ['/dashboard', '/operations', '/vehicles', '/drivers', '/trips', '/routes', '/stations', '/schedules', '/alerts', '/reports', '/users'];
  for (const path of adminPaths) {
    const resolved = router.resolve(path); expect(resolved.meta.role).toBe('ADMIN'); expect(resolved.matched.slice(-1)[0]?.path).toBe(path);
  }
  for (const path of ['/login', '/register']) expect(router.resolve(path).meta.guestOnly).toBe(true);
  for (const path of ['/driver/today', '/driver/schedules']) expect(router.resolve(path).meta.role).toBe('DRIVER');
  expect(router.resolve('/').matched.slice(-1)[0]?.redirect).toBe('/dashboard');
  expect(router.resolve('/unknown-fixture').matched.slice(-1)[0]?.path).toBe('/:pathMatch(.*)*');
  expect(router.resolve('/unknown-fixture').meta.role).toBe('ADMIN');
});

test('actual route graph enforces driver/admin/guest boundaries before mounting a page', async () => {
  const cases = [
    { user: admin, path: '/driver/today', expected: '/dashboard' },
    { user: { ...admin, role: 'DRIVER' as const, driverId: 1 }, path: '/reports', expected: '/driver/today' },
    { user: null, path: '/operations?mode=simulation', expected: '/login' },
  ];
  for (const { user, path, expected } of cases) {
    vi.mocked(fetchCurrentUser).mockResolvedValue(user as AuthUser);
    const router = createApplicationRouter(createMemoryHistory()), auth = createAuthState();
    const dispose = installAuthGuards(router, auth); cleanups.push(dispose);
    await router.push(path); expect(router.currentRoute.value.path).toBe(expected);
    if (!user) expect(router.options.history.state.from).toBe('/operations');
  }
});

test('map deep links, repeated query values and back/forward reuse the operations owner; route workspace remounts it', async () => {
  const router = createApplicationRouter(createMemoryHistory()), auth = createAuthState();
  const dispose = installAuthGuards(router, auth); cleanups.push(dispose);
  await router.push('/operations?tripId=7');
  const wrapper = mount(App, { global: { plugins: [router], provide: { [authKey as symbol]: auth } } }); cleanups.push(() => wrapper.unmount());
  await flushPromises(); const map = () => wrapper.get('[data-workspace]');
  expect(map().attributes('data-trip')).toBe('7'); expect(lifecycle.mounted).toHaveBeenCalledTimes(1);
  await map().get('button').trigger('click'); await flushPromises();
  expect(router.currentRoute.value.fullPath).toBe('/operations?mode=simulation'); expect(map().attributes('data-workspace')).toBe('simulation');
  router.back(); await flushPromises(); expect(map().attributes('data-trip')).toBe('7'); expect(map().attributes('data-workspace')).toBe('tracking');
  router.forward(); await flushPromises(); expect(map().attributes('data-workspace')).toBe('simulation');
  await router.push('/operations?mode=simulation&mode=tracking&tripId=8&tripId=9'); await flushPromises();
  expect(map().attributes('data-trip')).toBe('8'); expect(map().attributes('data-workspace')).toBe('simulation');
  await router.push('/operations?tripId=-1'); await flushPromises(); expect(map().attributes('data-trip')).toBeUndefined();
  expect(lifecycle.mounted).toHaveBeenCalledTimes(1); expect(lifecycle.unmounted).not.toHaveBeenCalled();
  await router.push('/routes?tripId=7'); await flushPromises(); expect(map().attributes('data-workspace')).toBe('routes'); expect(map().attributes('data-trip')).toBeUndefined();
  expect(lifecycle.mounted).toHaveBeenCalledTimes(2); expect(lifecycle.unmounted).toHaveBeenCalledTimes(1);
});

test('fleet query updates reuse a page; changing tabs resets the workspace owner', async () => {
  const router = createApplicationRouter(createMemoryHistory()), auth = createAuthState();
  cleanups.push(installAuthGuards(router, auth)); await router.push('/trips?vehicleId=4&vehicleId=5');
  const wrapper = mount(App, { global: { plugins: [router], provide: { [authKey as symbol]: auth } } }); cleanups.push(() => wrapper.unmount());
  await flushPromises(); const fleet = () => wrapper.get('[data-fleet]');
  expect(fleet().attributes('data-filter')).toBe('4'); const instance = fleet().attributes('data-instance');
  await router.replace('/trips?vehicleId=invalid'); await flushPromises();
  expect(fleet().attributes('data-filter')).toBeUndefined(); expect(fleet().attributes('data-instance')).toBe(instance);
  await router.push('/vehicles?vehicleId=4'); await flushPromises();
  expect(fleet().attributes('data-fleet')).toBe('vehicles'); expect(fleet().attributes('data-filter')).toBeUndefined();
  expect(fleet().attributes('data-instance')).not.toBe(instance);
});
