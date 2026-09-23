import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory } from 'vue-router';
import App from '../../src/App.vue';
import { createApplicationRouter } from '@/app/router';
import { createAuthState } from '@/features/auth/composables/authState';
import { authKey } from '@/features/auth/composables/useAuth';
import { installAuthGuards } from '@/app/router/guards';
import type { AuthUser } from '@/features/auth/types/auth';
import type { TripDetail, TripSummary } from '@/features/fleet/types/fleet';
import type { TripSchedule } from '@/features/schedules/types/schedule';
import type { NotificationItem } from '@/features/reports/types/notifications';
import type { DashboardSummary } from '@/features/reports/types/dashboard';

// Real pages, router, auth state and HTTP services. Only the network and native
// dialog top-layer APIs are fixtures; this is not a real backend/browser E2E test.
const stamp = '2026-09-22T17:30:00Z'; // 00:30 on 23 September in the business timezone.
const driver: AuthUser = { accountId: 2, username: 'driver.fixture', role: 'DRIVER', active: true, driverId: 1, driverName: 'Fixture driver' };
const admin: AuthUser = { ...driver, accountId: 1, username: 'admin.fixture', role: 'ADMIN', driverId: null, driverName: null };
const trips: TripSummary[] = [
  { id: 100, attemptNumber: 1, vehicleId: 1, vehiclePlateNumber: '51B12345', vehicleType: 'CAR', routeId: 1, routeName: 'Trip A', status: 'SCHEDULED',
    scheduledDepartureAt: '2026-09-22T17:00:00Z', plannedEndAt: '2026-09-23T02:00:00Z', startedAt: null, endedAt: null, createdAt: stamp, driver: null },
  { id: 101, attemptNumber: 1, vehicleId: 2, vehiclePlateNumber: '51B23456', vehicleType: 'CAR', routeId: 1, routeName: 'Trip B', status: 'COMPLETED',
    scheduledDepartureAt: '2026-09-22T16:59:59Z', plannedEndAt: stamp, startedAt: stamp, endedAt: stamp, createdAt: stamp, driver: null },
];
const detail = (trip: TripSummary): TripDetail => ({ trip, stops: [], route: { id: 1, name: 'Fixture route', transportMode: 'CAR', routingProvider: 'HERE', totalDistanceMeters: 1000,
  estimatedTravelDurationSeconds: 600, baseTravelDurationSeconds: 600, totalDwellDurationSeconds: 0, estimatedTripDurationSeconds: 600,
  estimatedDepartureAt: stamp, calculatedAt: stamp, createdAt: stamp, stops: [], sections: [] } });
const schedule: TripSchedule = { id: 1, name: 'Morning', routeId: 1, routeName: 'Fixture route', vehicleId: 1, vehiclePlate: '51B12345', driverId: 1, driverName: driver.driverName!,
  frequency: 'WEEKLY', scheduledDate: null, weekdaysMask: 31, departureTime: '08:00:00', timezone: 'Asia/Ho_Chi_Minh', effectiveFrom: '2026-09-01', effectiveUntil: null,
  enabled: true, nextRunAt: stamp, lastRunAt: null, lastRunStatus: null, lastRunMessage: null };
const alert: NotificationItem = { id: 1, tripId: 100, vehicleId: 1, vehiclePlateNumber: '51B12345', revisionId: null, type: 'OFF_ROUTE_DETECTED', severity: 'CRITICAL', title: 'Off-route fixture',
  reason: 'Fixture reason', incidentId: null, affectedStopSequences: '', baselineEtaSeconds: null, revisedEtaSeconds: null, createdAt: stamp, readAt: null,
  measuredDistanceMeters: 450, thresholdDistanceMeters: 200, breachDurationSeconds: 90 };
const summary: DashboardSummary = { serverTime: stamp, activeVehicleCount: 4, activeDriverCount: 3, tripsInProgress: 1, scheduledTrips: 1, completedTrips: 12,
  cancelledTrips: 2, overdueTrips: 1, offRouteVehicleCount: 1, unreadAlertCount: 1, pendingAlerts: [alert] };
interface RequestRecord { path: string; method: string; options: RequestInit }
const requests: RequestRecord[] = [], disposals: (() => void)[] = [];
let user: AuthUser = driver;
const handlers = new Map<string, (request: RequestRecord) => Response | Promise<Response>>();
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } });
const calls = (path: string, method = 'GET') => requests.filter(request => request.path === path && request.method === method);
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(r => { resolve = r; }); return { promise, resolve }; }
const originalShow = Object.getOwnPropertyDescriptor(HTMLDialogElement.prototype, 'showModal'), originalClose = Object.getOwnPropertyDescriptor(HTMLDialogElement.prototype, 'close');
beforeEach(() => {
  requests.length = 0; handlers.clear(); user = driver;
  vi.useFakeTimers({ toFake: ['Date', 'setInterval', 'clearInterval'] }); vi.setSystemTime(stamp);
  document.cookie = 'XSRF-TOKEN=fixture-csrf; Path=/';
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, options: RequestInit = {}) => {
    const url = new URL(String(input), 'http://localhost'); const request = { path: url.pathname + url.search, method: options.method ?? 'GET', options };
    requests.push(request); const handler = handlers.get(`${request.method} ${request.path}`);
    if (handler) return handler(request);
    if (request.path === '/api/v1/auth/me') return json(user);
    if (request.path === '/api/v1/auth/logout') return new Response(null, { status: 204 });
    if (request.path === '/api/v1/driver/trips' || request.path === '/api/v1/trips') return json(trips);
    if (request.path === '/api/v1/driver/schedules') return json([schedule]);
    if (request.path === '/api/v1/notifications?unreadOnly=false') return json([alert, { ...alert, id: 2, type: 'REROUTE_CREATED', severity: 'MAJOR' }]);
    if (request.path === '/api/v1/dashboard/summary') return json(summary);
    throw new Error(`Unexpected fixture request ${request.method} ${request.path}`);
  }));
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', { configurable: true, value(this: HTMLDialogElement) { this.open = true; } });
  Object.defineProperty(HTMLDialogElement.prototype, 'close', { configurable: true, value(this: HTMLDialogElement) { this.open = false; } });
});
afterEach(() => {
  disposals.splice(0).reverse().forEach(dispose => dispose()); vi.useRealTimers(); vi.unstubAllGlobals(); document.body.replaceChildren();
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/';
  for (const [key, descriptor] of [['showModal', originalShow], ['close', originalClose]] as const) {
    if (descriptor) Object.defineProperty(HTMLDialogElement.prototype, key, descriptor); else Reflect.deleteProperty(HTMLDialogElement.prototype, key);
  }
});
async function open(path: string) {
  const auth = createAuthState(), router = createApplicationRouter(createMemoryHistory());
  disposals.push(installAuthGuards(router, auth)); await router.push(path);
  const wrapper = mount(App, { attachTo: document.body, global: { plugins: [router], provide: { [authKey as symbol]: auth } } });
  disposals.push(() => wrapper.unmount()); await flushPromises(); return { wrapper, router, auth };
}

test('driver redirects from admin URLs, fetches only assigned resources, keeps business-date boundary and tab reuse', async () => {
  const { wrapper, router } = await open('/reports');
  expect(router.currentRoute.value.path).toBe('/driver/today');
  expect(wrapper.findAll('.driver-summary-grid strong').map(item => item.text())).toEqual(['1', '#100', '1']);
  await wrapper.get('a[href="/driver/schedules"]').trigger('click'); await flushPromises();
  expect(wrapper.get('.driver-schedule-card').text()).toContain('Hàng tuần · 08:00');
  router.back(); await flushPromises(); expect(wrapper.findAll('.driver-trip-card')).toHaveLength(2);
  expect(requests.map(request => request.path)).toEqual(['/api/v1/auth/me', '/api/v1/driver/trips', '/api/v1/driver/schedules']);
  expect(requests.every(request => request.options.credentials === 'include')).toBe(true);
});

test('driver detail A cannot replace B; unmount aborts outstanding detail and list reads', async () => {
  const first = deferred<Response>(), second = deferred<Response>();
  handlers.set('GET /api/v1/driver/trips/100', () => first.promise); handlers.set('GET /api/v1/driver/trips/101', () => second.promise);
  const { wrapper } = await open('/driver/today'); const cards = wrapper.findAll('.driver-trip-card');
  await cards[0].trigger('click'); await flushPromises(); await cards[1].trigger('click'); await flushPromises();
  expect(calls('/api/v1/driver/trips/100')[0].options.signal?.aborted).toBe(true);
  second.resolve(json(detail(trips[1]))); await flushPromises(); first.resolve(json(detail(trips[0]))); await flushPromises();
  expect(wrapper.get('.driver-detail h2').text()).toBe('Trip B');
  await wrapper.get('[aria-label="Đóng chi tiết"]').trigger('click'); expect(wrapper.find('dialog').exists()).toBe(false);
  const pending = deferred<Response>(); handlers.set('GET /api/v1/driver/trips/100', () => pending.promise);
  await cards[0].trigger('click'); await flushPromises(); wrapper.unmount();
  expect(calls('/api/v1/driver/trips/100')[1].options.signal?.aborted).toBe(true);
  expect(calls('/api/v1/driver/trips')[0].options.signal?.aborted).toBe(true);
  pending.resolve(json(detail(trips[0]))); await flushPromises(); expect(document.querySelector('dialog')).toBeNull();
});

test('driver API problem details render with retry; logout sends credentialed CSRF request and clears role access', async () => {
  handlers.set('GET /api/v1/driver/trips', () => json({ detail: 'Fixture temporarily unavailable' }, 503));
  const { wrapper, router, auth } = await open('/driver/today');
  expect(wrapper.get('[role=alert]').text()).toContain('Fixture temporarily unavailable');
  handlers.delete('GET /api/v1/driver/trips'); await wrapper.get('.driver-error button').trigger('click'); await flushPromises();
  expect(wrapper.find('[role=alert]').exists()).toBe(false); expect(wrapper.findAll('.driver-trip-card')).toHaveLength(2);
  await wrapper.get('.driver-portal-account button').trigger('click'); await flushPromises();
  expect(router.currentRoute.value.path).toBe('/login'); expect(auth.user).toBeNull();
  const request = calls('/api/v1/auth/logout', 'POST')[0]; expect(request.options.credentials).toBe('include');
  expect(new Headers(request.options.headers).get('X-XSRF-TOKEN')).toBe('fixture-csrf');
});

test('alerts filters, single read and read-all preserve HTTP payload and duplicate-submit lock', async () => {
  user = admin; const pending = deferred<Response>(); handlers.set('POST /api/v1/notifications/1/read', () => pending.promise);
  handlers.set('POST /api/v1/notifications/read-all', () => json({ updated: 1 }));
  const { wrapper } = await open('/alerts'); const filters = wrapper.findAll('.alerts-filters select');
  await filters[0].setValue('OFF_ROUTE_DETECTED'); expect(wrapper.findAll('.alerts-management-card')).toHaveLength(1);
  expect(wrapper.get('.alerts-management-body > strong').text()).toBe('Khoảng cách 450 m · ngưỡng 200 m · duy trì 90s');
  expect(wrapper.get('.alerts-management-actions a').attributes('href')).toBe('/operations?tripId=100');
  const mark = wrapper.get('.alerts-management-actions button'); await mark.trigger('click'); await mark.trigger('click'); await flushPromises();
  expect(calls('/api/v1/notifications/1/read', 'POST')).toHaveLength(1); expect(wrapper.get('.alerts-read-all').attributes('disabled')).toBeDefined();
  pending.resolve(json({ ...alert, readAt: stamp })); await flushPromises(); expect(wrapper.get('.alerts-management-card').classes()).toContain('read');
  await filters[0].setValue('ALL'); await filters[1].setValue('MAJOR'); expect(wrapper.findAll('.alerts-management-card')).toHaveLength(1);
  await wrapper.get('.alerts-read-all').trigger('click'); await flushPromises();
  expect(calls('/api/v1/notifications/read-all', 'POST')).toHaveLength(1); expect(wrapper.get('.alerts-read-all').attributes('disabled')).toBeDefined();
  for (const request of requests.filter(request => request.method === 'POST')) {
    expect(request.options.body).toBeUndefined(); expect(new Headers(request.options.headers).get('X-XSRF-TOKEN')).toBe('fixture-csrf');
  }
});

test('alert deletion stays behind confirmation, preserves conflict for retry and only removes acknowledged item', async () => {
  user = admin; handlers.set('DELETE /api/v1/notifications/1', () => json({ detail: 'Fixture conflict' }, 409));
  const { wrapper } = await open('/alerts');
  await wrapper.findAll('.alerts-management-card')[0].findAll('button')[1].trigger('click'); expect(calls('/api/v1/notifications/1', 'DELETE')).toHaveLength(0);
  await wrapper.get('dialog .danger-action').trigger('click'); await flushPromises();
  expect(wrapper.get('dialog [role=alert]').text()).toContain('HTTP 409'); expect(wrapper.findAll('.alerts-management-card')).toHaveLength(2);
  const pending = deferred<Response>(); handlers.set('DELETE /api/v1/notifications/1', () => pending.promise);
  await wrapper.get('dialog .danger-action').trigger('click'); await flushPromises(); await wrapper.get('dialog').trigger('cancel');
  expect(wrapper.find('dialog').exists()).toBe(true);
  pending.resolve(new Response(null, { status: 204 })); await flushPromises();
  expect(wrapper.find('dialog').exists()).toBe(false); expect(wrapper.findAll('.alerts-management-card')).toHaveLength(1);
});

test('alerts and dashboard polling stop after navigation and use server time for overdue status', async () => {
  user = admin; const { wrapper, router } = await open('/alerts');
  await vi.advanceTimersByTimeAsync(15_000); await flushPromises(); expect(calls('/api/v1/notifications?unreadOnly=false')).toHaveLength(2);
  handlers.set('GET /api/v1/trips', () => json([{ ...trips[0], status: 'IN_PROGRESS', plannedEndAt: '2026-09-22T17:29:59Z' }, trips[1]]));
  await router.push('/dashboard'); await flushPromises();
  expect(calls('/api/v1/notifications?unreadOnly=false')[0].options.signal?.aborted).toBe(true);
  expect(wrapper.findAll('.dashboard-trip-id').map(item => item.text())).toEqual(['#100', '#101']);
  expect(wrapper.get('.dashboard-status.in_progress').text()).toBe('Đang trễ');
  await vi.advanceTimersByTimeAsync(15_000); await flushPromises(); expect(calls('/api/v1/dashboard/summary')).toHaveLength(2);
  expect(calls('/api/v1/notifications?unreadOnly=false')).toHaveLength(2);
  wrapper.unmount(); expect(calls('/api/v1/dashboard/summary')[0].options.signal?.aborted).toBe(true);
  await vi.advanceTimersByTimeAsync(30_000); await flushPromises(); expect(calls('/api/v1/dashboard/summary')).toHaveLength(2);
});
