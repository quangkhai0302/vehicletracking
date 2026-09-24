import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory } from 'vue-router';
import App from '../../src/App.vue';
import { createApplicationRouter } from '@/app/router';
import { createAuthState } from '@/features/auth/composables/authState';
import { authKey } from '@/features/auth/composables/useAuth';
import { installAuthGuards } from '@/app/router/guards';
import { findRoute, navigationGroups } from '@/app/navigation';
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
  return { default: defineComponent({ props: ['initialTab', 'initialVehicleFilter', 'initialRouteId', 'openTripFromRoute', 'onSimulateTrip', 'onViewRoute'], setup(props) {
    const instance = getCurrentInstance()!.uid;
    return () => h('div', {
      'data-fleet': props.initialTab,
      'data-filter': props.initialVehicleFilter,
      'data-route': props.initialRouteId,
      'data-open-route-trip': props.openTripFromRoute,
      'data-instance': instance,
    }, [
      h('button', { 'data-simulate-trip': '42', onClick: () => props.onSimulateTrip(42) }, 'Mô phỏng chuyến 42'),
      h('button', { 'data-view-route': '7', onClick: () => props.onViewRoute(7) }, 'Xem tuyến 7'),
    ]);
  } }) };
});
const admin: AuthUser = { accountId: 1, username: 'admin.fixture', role: 'ADMIN', active: true, driverId: null, driverName: null };
const cleanups: (() => void)[] = [];
const originalShowModal = Object.getOwnPropertyDescriptor(
  HTMLDialogElement.prototype,
  'showModal',
);
const originalDialogClose = Object.getOwnPropertyDescriptor(HTMLDialogElement.prototype, 'close');
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(fetchCurrentUser).mockResolvedValue(admin);
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', {
    configurable: true,
    value(this: HTMLDialogElement) {
      this.open = true;
    },
  });
  Object.defineProperty(HTMLDialogElement.prototype, 'close', {
    configurable: true,
    value(this: HTMLDialogElement) {
      this.open = false;
    },
  });
});
afterEach(() => {
  cleanups.splice(0).forEach(dispose => dispose());
  vi.unstubAllGlobals();
  for (const [key, descriptor] of [
    ['showModal', originalShowModal],
    ['close', originalDialogClose],
  ] as const) {
    if (descriptor) Object.defineProperty(HTMLDialogElement.prototype, key, descriptor);
    else Reflect.deleteProperty(HTMLDialogElement.prototype, key);
  }
});

test('planning navigation is represented once and resolves both legacy URLs', () => {
  const planningItems = navigationGroups
    .flatMap((group) => group.items)
    .filter((item) => item.path === '/trips' || item.aliases?.includes('/routes'));
  expect(planningItems).toHaveLength(1);
  expect(planningItems[0]).toMatchObject({
    path: '/trips',
    aliases: ['/routes'],
    title: 'Kế hoạch vận hành',
  });
  expect(findRoute('/routes')).toBe(planningItems[0]);
  expect(findRoute('/trips')).toBe(planningItems[0]);
});

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

test('legacy route and trip URLs share the planning workspace while selecting the correct tab', async () => {
  const router = createApplicationRouter(createMemoryHistory()), auth = createAuthState();
  cleanups.push(installAuthGuards(router, auth));
  await router.push('/routes');
  const wrapper = mount(App, {
    global: { plugins: [router], provide: { [authKey as symbol]: auth } },
  });
  cleanups.push(() => wrapper.unmount());
  await flushPromises();
  const workspace = wrapper.get('.planning-workspace').element;
  expect(wrapper.get('.planning-workspace-tabs a[href="/routes"]').attributes('aria-current')).toBe('page');
  expect(wrapper.get('.business-navigation a[href="/trips"]').classes()).toContain('active');

  await wrapper.get('.planning-workspace-tabs a[href="/trips"]').trigger('click');
  await flushPromises();
  expect(router.currentRoute.value.path).toBe('/trips');
  expect(wrapper.get('.planning-workspace').element).toBe(workspace);
  expect(wrapper.get('.planning-workspace-tabs a[href="/trips"]').attributes('aria-current')).toBe('page');
  expect(wrapper.get('[data-fleet]').attributes('data-fleet')).toBe('trips');

  router.back();
  await flushPromises();
  expect(router.currentRoute.value.path).toBe('/routes');
  expect(wrapper.get('.planning-workspace-tabs a[href="/routes"]').attributes('aria-current')).toBe('page');
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
  expect(router.currentRoute.value.fullPath).toBe('/operations?mode=simulation&tripId=7'); expect(map().attributes('data-workspace')).toBe('simulation');
  router.back(); await flushPromises(); expect(map().attributes('data-trip')).toBe('7'); expect(map().attributes('data-workspace')).toBe('tracking');
  router.forward(); await flushPromises(); expect(map().attributes('data-workspace')).toBe('simulation');
  await router.push('/operations?mode=simulation&mode=tracking&tripId=8&tripId=9'); await flushPromises();
  expect(map().attributes('data-trip')).toBe('8'); expect(map().attributes('data-workspace')).toBe('simulation');
  await router.push('/operations?tripId=-1'); await flushPromises(); expect(map().attributes('data-trip')).toBeUndefined();
  expect(lifecycle.mounted).toHaveBeenCalledTimes(1); expect(lifecycle.unmounted).not.toHaveBeenCalled();
  await router.push('/routes?tripId=7'); await flushPromises();
  expect(wrapper.find('[data-workspace]').exists()).toBe(false);
  expect(wrapper.find('.routes-page').exists()).toBe(true);
  expect(lifecycle.unmounted).toHaveBeenCalledTimes(1);
  await router.push('/operations'); await flushPromises();
  expect(wrapper.find('[data-workspace]').exists()).toBe(true);
  expect(lifecycle.mounted).toHaveBeenCalledTimes(2);
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

test('route and trip cross-links preserve the selected route without creating data automatically', async () => {
  const routeSummary = {
    id: 7,
    name: 'Tuyến thử nghiệm',
    transportMode: 'CAR',
    routingProvider: 'HERE',
    startStationName: 'Điểm A',
    endStationName: 'Điểm B',
    stopCount: 2,
    totalDistanceMeters: 1200,
    estimatedTravelDurationSeconds: 600,
    totalDwellDurationSeconds: 0,
    estimatedTripDurationSeconds: 600,
    calculatedAt: '2026-09-24T01:00:00Z',
    createdAt: '2026-09-24T01:00:00Z',
    active: true,
  };
  const routeDetail = {
    ...routeSummary,
    baseTravelDurationSeconds: 600,
    estimatedDepartureAt: '2026-09-24T01:00:00Z',
    stops: [],
    sections: [],
  };
  const fetch = vi.fn(async (input: RequestInfo | URL) => {
    const path = new URL(String(input), 'http://localhost').pathname;
    if (path === '/api/v1/routes')
      return new Response(JSON.stringify([routeSummary]), {
        headers: { 'Content-Type': 'application/json' },
      });
    if (path === '/api/v1/routes/7')
      return new Response(JSON.stringify(routeDetail), {
        headers: { 'Content-Type': 'application/json' },
      });
    if (path === '/api/v1/stations')
      return new Response(JSON.stringify([]), {
        headers: { 'Content-Type': 'application/json' },
      });
    throw new Error(`Unexpected request ${path}`);
  });
  vi.stubGlobal('fetch', fetch);

  const router = createApplicationRouter(createMemoryHistory()), auth = createAuthState();
  cleanups.push(installAuthGuards(router, auth));
  await router.push('/routes?routeId=7');
  const wrapper = mount(App, {
    global: { plugins: [router], provide: { [authKey as symbol]: auth } },
  });
  cleanups.push(() => wrapper.unmount());
  await flushPromises();

  const createTrip = wrapper
    .findAll('button')
    .find((button) => button.text().includes('Tạo chuyến từ tuyến này'));
  expect(createTrip).toBeDefined();
  await createTrip!.trigger('click');
  await flushPromises();
  expect(router.currentRoute.value.fullPath).toBe('/trips?routeId=7&create=1');
  expect(wrapper.get('[data-fleet]').attributes('data-route')).toBe('7');
  expect(wrapper.get('[data-fleet]').attributes('data-open-route-trip')).toBe('true');
  expect(fetch).not.toHaveBeenCalledWith(
    expect.stringContaining('/api/v1/trips'),
    expect.objectContaining({ method: 'POST' }),
  );

  await wrapper.get('[data-view-route="7"]').trigger('click');
  await flushPromises();
  expect(router.currentRoute.value.fullPath).toBe('/routes?routeId=7');
});

test('invalid trip route query does not preselect or open the create form', async () => {
  const router = createApplicationRouter(createMemoryHistory()), auth = createAuthState();
  cleanups.push(installAuthGuards(router, auth));
  await router.push('/trips?routeId=invalid&create=1');
  const wrapper = mount(App, {
    global: { plugins: [router], provide: { [authKey as symbol]: auth } },
  });
  cleanups.push(() => wrapper.unmount());
  await flushPromises();
  expect(wrapper.get('[data-fleet]').attributes('data-route')).toBeUndefined();
  expect(wrapper.get('[data-fleet]').attributes('data-open-route-trip')).toBe('false');
});

test('opening a trip simulator from management carries its exact trip into the simulation map', async () => {
  const router = createApplicationRouter(createMemoryHistory()), auth = createAuthState();
  cleanups.push(installAuthGuards(router, auth));
  await router.push('/trips');
  const wrapper = mount(App, { global: { plugins: [router], provide: { [authKey as symbol]: auth } } });
  cleanups.push(() => wrapper.unmount());
  await flushPromises();
  await wrapper.get('[data-simulate-trip="42"]').trigger('click');
  await flushPromises();
  expect(router.currentRoute.value.fullPath).toBe('/operations?mode=simulation&tripId=42');
  expect(wrapper.get('[data-workspace]').attributes('data-trip')).toBe('42');
  expect(wrapper.get('[data-workspace]').attributes('data-workspace')).toBe('simulation');
});
