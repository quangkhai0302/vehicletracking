import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { DOMWrapper, flushPromises, mount } from '@vue/test-utils';
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
import { notifyError } from '@/shared/notifications/toast';
import AlertStream from '@/features/tracking/components/AlertStream.vue';

// The admin shell owns one realtime stream; page workflow assertions still use the HTTP fixtures below.
vi.mock('@/features/tracking/api/operations', () => ({
  fetchOperations: async () => ({ serverTime: new Date().toISOString(), positions: [], trips: [], simulations: [], checkIns: [], notifications: [] }),
  subscribeOperations: () => () => {},
}));
// These workflows assert notification navigation rather than Leaflet rendering.
vi.mock('@/features/map/components/MapComponent.vue', () => ({
  __esModule: true,
  default: { template: '<div data-workflow-map-fixture />' },
}));

vi.mock('@/shared/notifications/toast', () => ({
  errorMessage: (reason: unknown, fallback = 'Đã xảy ra lỗi. Vui lòng thử lại.') =>
    reason instanceof Error ? reason.message : fallback,
  notifyError: vi.fn(),
  notifySuccess: vi.fn(),
  notifyWarning: vi.fn(),
  notifyInfo: vi.fn(),
  notifyLegacy: vi.fn(),
}));

// Real pages, router, auth state and HTTP services. Only the network and native
// dialog top-layer APIs are fixtures; this is not a real backend/browser E2E test.
const stamp = '2026-09-22T17:30:00Z'; // 00:30 on 23 September in the business timezone.
const driver: AuthUser = {
  passwordChangeRequired: false,
  accountId: 2,
  username: 'driver.fixture',
  role: 'DRIVER',
  active: true,
  driverId: 1,
  driverName: 'Fixture driver',
};
const admin: AuthUser = {
  ...driver,
  passwordChangeRequired: false,
  accountId: 1,
  username: 'admin.fixture',
  role: 'ADMIN',
  driverId: null,
  driverName: null,
};
const trips: TripSummary[] = [
  {
    id: 100,
    attemptNumber: 1,
    vehicleId: 1,
    vehiclePlateNumber: '51B12345',
    vehicleType: 'CAR',
    routeId: 1,
    routeName: 'Trip A',
    status: 'SCHEDULED',
    scheduledDepartureAt: '2026-09-22T17:00:00Z',
    plannedEndAt: '2026-09-23T02:00:00Z',
    startedAt: null,
    endedAt: null,
    createdAt: stamp,
    dispatchMode: 'FIXED_SCHEDULE',
    scheduleId: 1,
    scheduleName: 'Lịch sáng',
    driver: null,
  },
  {
    id: 101,
    attemptNumber: 1,
    vehicleId: 2,
    vehiclePlateNumber: '51B23456',
    vehicleType: 'CAR',
    routeId: 1,
    routeName: 'Trip B',
    status: 'COMPLETED',
    scheduledDepartureAt: '2026-09-22T16:59:59Z',
    plannedEndAt: stamp,
    startedAt: stamp,
    endedAt: stamp,
    createdAt: stamp,
    dispatchMode: 'ON_DEMAND',
    scheduleId: null,
    scheduleName: null,
    driver: null,
  },
];
const detail = (trip: TripSummary): TripDetail => ({
  trip,
  stops: [],
  route: {
    id: 1,
    name: 'Fixture route',
    transportMode: 'CAR',
    routingProvider: 'HERE',
    totalDistanceMeters: 1000,
    estimatedTravelDurationSeconds: 600,
    baseTravelDurationSeconds: 600,
    totalDwellDurationSeconds: 0,
    estimatedTripDurationSeconds: 600,
    estimatedDepartureAt: stamp,
    calculatedAt: stamp,
    createdAt: stamp,
    stops: [],
    sections: [],
  },
});
const schedule: TripSchedule = {
  id: 1,
  name: 'Morning',
  routeId: 1,
  routeName: 'Fixture route',
  vehicleId: 1,
  vehiclePlate: '51B12345',
  driverId: 1,
  driverName: driver.driverName!,
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
const alert: NotificationItem = {
  id: 1,
  tripId: 100,
  vehicleId: 1,
  vehiclePlateNumber: '51B12345',
  revisionId: null,
  type: 'OFF_ROUTE_DETECTED',
  severity: 'CRITICAL',
  title: 'Off-route fixture',
  reason: 'Fixture reason',
  incidentId: null,
  affectedStopSequences: '',
  baselineEtaSeconds: null,
  revisedEtaSeconds: null,
  createdAt: stamp,
  readAt: null,
  measuredDistanceMeters: 450,
  thresholdDistanceMeters: 200,
  breachDurationSeconds: 90,
};
const summary: DashboardSummary = {
  serverTime: stamp,
  activeVehicleCount: 4,
  activeDriverCount: 3,
  tripsInProgress: 1,
  scheduledTrips: 1,
  completedTrips: 12,
  cancelledTrips: 2,
  overdueTrips: 1,
  offRouteVehicleCount: 1,
  unreadAlertCount: 1,
  pendingAlerts: [alert],
};
interface RequestRecord {
  path: string;
  method: string;
  options: RequestInit;
}
const requests: RequestRecord[] = [],
  disposals: (() => void)[] = [];
let user: AuthUser = driver;
const handlers = new Map<string, (request: RequestRecord) => Response | Promise<Response>>();
const json = (value: unknown, status = 200) =>
  new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } });
const calls = (path: string, method = 'GET') =>
  requests.filter((request) => request.path === path && request.method === method);
const documentView = () => new DOMWrapper(document.body);
const notificationPanel = () => documentView().get('.admin-notification-panel');
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((r) => {
    resolve = r;
  });
  return { promise, resolve };
}
const originalShow = Object.getOwnPropertyDescriptor(HTMLDialogElement.prototype, 'showModal'),
  originalClose = Object.getOwnPropertyDescriptor(HTMLDialogElement.prototype, 'close');
beforeEach(() => {
  requests.length = 0;
  handlers.clear();
  user = driver;
  vi.useFakeTimers({ toFake: ['Date', 'setInterval', 'clearInterval'] });
  vi.setSystemTime(stamp);
  document.cookie = 'XSRF-TOKEN=fixture-csrf; Path=/';
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, options: RequestInit = {}) => {
      const url = new URL(String(input), 'http://localhost');
      const request = { path: url.pathname + url.search, method: options.method ?? 'GET', options };
      requests.push(request);
      const handler = handlers.get(`${request.method} ${request.path}`);
      if (handler) return handler(request);
      if (request.path === '/api/v1/auth/me') return json(user);
      if (request.path === '/api/v1/auth/logout') return new Response(null, { status: 204 });
      if (request.path === '/api/v1/driver/trips' || request.path === '/api/v1/trips')
        return json(trips);
      if (request.path === '/api/v1/driver/schedules') return json([schedule]);
      if (request.path === '/api/v1/driver/assignment-requests'
          || request.path === '/api/v1/driver/dispatch/inbox?limit=50') return json([]);
      if (request.path === '/api/v1/notifications?unreadOnly=false')
        return json([alert, { ...alert, id: 2, type: 'REROUTE_CREATED', severity: 'MAJOR' }]);
      if (request.path === '/api/v1/dashboard/summary') return json(summary);
      throw new Error(`Unexpected fixture request ${request.method} ${request.path}`);
    }),
  );
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
  disposals
    .splice(0)
    .reverse()
    .forEach((dispose) => dispose());
  vi.useRealTimers();
  vi.unstubAllGlobals();
  document.body.replaceChildren();
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/';
  for (const [key, descriptor] of [
    ['showModal', originalShow],
    ['close', originalClose],
  ] as const) {
    if (descriptor) Object.defineProperty(HTMLDialogElement.prototype, key, descriptor);
    else Reflect.deleteProperty(HTMLDialogElement.prototype, key);
  }
});
async function open(path: string) {
  const auth = createAuthState(),
    router = createApplicationRouter(createMemoryHistory());
  disposals.push(installAuthGuards(router, auth));
  await router.push(path);
  const wrapper = mount(App, {
    attachTo: document.body,
    global: { plugins: [router], provide: { [authKey as symbol]: auth } },
  });
  disposals.push(() => wrapper.unmount());
  await flushPromises();
  return { wrapper, router, auth };
}

test('driver redirects from admin URLs, fetches only assigned resources, keeps business-date boundary and tab reuse', async () => {
  const { wrapper, router } = await open('/reports');
  expect(router.currentRoute.value.path).toBe('/driver/today');
  expect(wrapper.findAll('.driver-summary-grid strong').map((item) => item.text())).toEqual([
    '2',
    '#100',
    '1',
  ]);
  const notificationBell = wrapper.get('.driver-portal-header .driver-account-trigger').element;
  const notificationCalls = calls('/api/v1/driver/dispatch/inbox?limit=50').length;
  await wrapper.get('a[href="/driver/schedules"]').trigger('click');
  await flushPromises();
  expect(wrapper.get('.driver-schedule-card').text()).toContain('Hàng tuần · 08:00');
  expect(wrapper.get('.driver-portal-header .driver-account-trigger').element).toBe(notificationBell);
  expect(calls('/api/v1/driver/dispatch/inbox?limit=50')).toHaveLength(notificationCalls);
  router.back();
  await flushPromises();
  expect(wrapper.findAll('.driver-trip-card')).toHaveLength(2);
  expect(requests.map((request) => request.path).slice(0, 3)).toEqual([
    '/api/v1/auth/me',
    '/api/v1/driver/trips',
    '/api/v1/driver/schedules',
  ]);
  expect(requests.map((request) => request.path).slice(3).every((path) =>
    path === '/api/v1/driver/assignment-requests'
      || path === '/api/v1/driver/dispatch/inbox?limit=50')).toBe(true);
  expect(requests.every((request) => request.options.credentials === 'include')).toBe(true);
});

test('driver detail A cannot replace B; unmount aborts outstanding detail and list reads', async () => {
  const first = deferred<Response>(),
    second = deferred<Response>();
  handlers.set('GET /api/v1/driver/trips/100', () => first.promise);
  handlers.set('GET /api/v1/driver/trips/101', () => second.promise);
  const { wrapper } = await open('/driver/today');
  const cards = wrapper.findAll('.driver-trip-card');
  await cards[0].trigger('click');
  await flushPromises();
  await cards[1].trigger('click');
  await flushPromises();
  expect(calls('/api/v1/driver/trips/100')[0].options.signal?.aborted).toBe(true);
  second.resolve(json(detail(trips[1])));
  await flushPromises();
  first.resolve(json(detail(trips[0])));
  await flushPromises();
  expect(wrapper.get('.driver-trip-detail h2').text()).toBe('Chi tiết chuyến #101');
  expect(wrapper.get('.driver-trip-detail .trip-summary-meta').text()).toContain('Trip B');
  await wrapper.get('[aria-label="Đóng chi tiết chuyến"]').trigger('click');
  expect(wrapper.find('dialog').exists()).toBe(false);
  const pending = deferred<Response>();
  handlers.set('GET /api/v1/driver/trips/100', () => pending.promise);
  await cards[0].trigger('click');
  await flushPromises();
  wrapper.unmount();
  expect(calls('/api/v1/driver/trips/100')[1].options.signal?.aborted).toBe(true);
  expect(calls('/api/v1/driver/trips')[0].options.signal?.aborted).toBe(true);
  pending.resolve(json(detail(trips[0])));
  await flushPromises();
  expect(document.querySelector('dialog')).toBeNull();
});

test('driver API problem uses toast and remains retryable; logout clears role access', async () => {
  handlers.set('GET /api/v1/driver/trips', () =>
    json({ detail: 'Fixture temporarily unavailable' }, 503),
  );
  const { wrapper, router, auth } = await open('/driver/today');
  expect(notifyError).toHaveBeenCalledWith('Fixture temporarily unavailable');
  handlers.delete('GET /api/v1/driver/trips');
  await wrapper.get('[aria-label="Tải lại chuyến được phân công"]').trigger('click');
  await flushPromises();
  expect(wrapper.findAll('.driver-trip-card')).toHaveLength(2);
  await wrapper.get('.driver-account-trigger').trigger('click');
  document.querySelector<HTMLButtonElement>('.driver-account-signout')!.click();
  await flushPromises();
  expect(router.currentRoute.value.path).toBe('/login');
  expect(auth.user).toBeNull();
  const request = calls('/api/v1/auth/logout', 'POST')[0];
  expect(request.options.credentials).toBe('include');
  expect(new Headers(request.options.headers).get('X-XSRF-TOKEN')).toBe('fixture-csrf');
});

test('driver header opens voluntary self-change without requesting more business data', async () => {
  const { wrapper, router, auth } = await open('/driver/today');
  const previousRequests = calls('/api/v1/driver/trips').length;
  await wrapper.get('.driver-account-trigger').trigger('click');
  const passwordButton = Array.from(document.querySelectorAll<HTMLButtonElement>('.driver-account-actions button')).find(button => button.textContent === 'Đổi mật khẩu');
  passwordButton!.click();
  await flushPromises();
  expect(router.currentRoute.value.path).toBe('/driver/change-password');
  expect(wrapper.get('h1').text()).toBe('Đổi mật khẩu');
  expect(wrapper.text()).toContain('Quay lại cổng tài xế');
  expect(auth.user?.passwordChangeRequired).toBe(false);
  expect(calls('/api/v1/driver/trips')).toHaveLength(previousRequests);
});

test('admin bell filters, single read and read-all preserve HTTP payload and duplicate-submit lock', async () => {
  user = admin;
  const pending = deferred<Response>();
  handlers.set('POST /api/v1/notifications/1/read', () => pending.promise);
  handlers.set('POST /api/v1/notifications/read-all', () => json({ updated: 1 }));
  const { wrapper, router } = await open('/alerts');
  const panel = notificationPanel();
  expect(panel.attributes('style')).toContain('max-height:');
  expect(router.currentRoute.value.path).toBe('/dashboard');
  expect(wrapper.find('a[href="/alerts"]').exists()).toBe(false);
  const filters = panel.findAll('.admin-notification-filters select');
  expect(filters[0].find('option[value="OFF_ROUTE_DETECTED"]').exists()).toBe(false);
  expect(wrapper.get('.admin-notification-trigger').attributes('aria-expanded')).toBe('true');
  await filters[0].setValue('REROUTE');
  expect(panel.findAll('.admin-notification-card')).toHaveLength(1);
  await filters[0].setValue('ALL');
  expect(panel.findAll('.admin-notification-card')).toHaveLength(2);
  expect(panel.get('.admin-notification-detail').text()).toBe(
    'Khoảng cách 450 m · ngưỡng 200 m · duy trì 90s',
  );
  expect(panel.get('.admin-notification-actions a').attributes('href')).toBe(
    '/operations?tripId=100',
  );
  const mark = panel.get('.admin-notification-actions button');
  await mark.trigger('click');
  await mark.trigger('click');
  await flushPromises();
  expect(calls('/api/v1/notifications/1/read', 'POST')).toHaveLength(1);
  expect(panel.get('.admin-notification-read-all').attributes('disabled')).toBeDefined();
  pending.resolve(json({ ...alert, readAt: stamp }));
  await flushPromises();
  expect(panel.get('.admin-notification-card').classes()).toContain('read');
  await filters[0].setValue('ALL');
  await filters[1].setValue('MAJOR');
  expect(panel.findAll('.admin-notification-card')).toHaveLength(1);
  await panel.get('.admin-notification-read-all').trigger('click');
  await flushPromises();
  expect(calls('/api/v1/notifications/read-all', 'POST')).toHaveLength(1);
  expect(panel.get('.admin-notification-read-all').attributes('disabled')).toBeDefined();
  for (const request of requests.filter((request) => request.method === 'POST')) {
    expect(request.options.body).toBeUndefined();
    expect(new Headers(request.options.headers).get('X-XSRF-TOKEN')).toBe('fixture-csrf');
  }
});

test('admin bell opens simulation incident monitoring and persists acknowledge and resolve actions', async () => {
  user = admin;
  const incidentNotice: NotificationItem = {
    ...alert,
    type: 'SIMULATION_INCIDENT',
    title: 'Xe gặp sự cố',
    reason: 'Xe gặp sự cố: Đã dừng kiểm tra',
    simulationIncidentId: 77,
    simulationIncidentStatus: 'OPEN',
    simulationIncidentType: 'VEHICLE_BREAKDOWN',
    simulationIncidentDetail: 'Đã dừng kiểm tra động cơ',
    simulationIncidentLatitude: 10.77,
    simulationIncidentLongitude: 106.7,
    simulationIncidentElapsedSeconds: 120,
  };
  handlers.set('GET /api/v1/notifications?unreadOnly=false', () => json([incidentNotice]));
  handlers.set('POST /api/v1/simulation-incidents/77/acknowledge', () => json({ status: 'ACKNOWLEDGED' }));
  handlers.set('POST /api/v1/simulation-incidents/77/resolve', () => json({ status: 'RESOLVED' }));
  const { router } = await open('/alerts');
  const panel = notificationPanel();
  await panel.findAll('.admin-notification-filters select')[0].setValue('INCIDENT');
  expect(panel.findAll('.admin-notification-card')).toHaveLength(1);
  expect(panel.text()).toContain('Vị trí mô phỏng 10.77000, 106.70000');
  expect(panel.text()).toContain('Đã dừng kiểm tra động cơ');
  expect(panel.get('.admin-notification-actions a').attributes('href')).toBe('/operations?tripId=100');
  await panel.get('.admin-notification-actions').findAll('button').find(button => button.text().includes('Tiếp nhận'))!.trigger('click');
  await flushPromises();
  expect(calls('/api/v1/simulation-incidents/77/acknowledge', 'POST')).toHaveLength(1);
  expect(panel.get('.admin-notification-incident-meta').text()).toContain('Đã tiếp nhận');
  await panel.get('.admin-notification-actions').findAll('button').find(button => button.text().includes('Đã xử lý'))!.trigger('click');
  await flushPromises();
  expect(calls('/api/v1/simulation-incidents/77/resolve', 'POST')).toHaveLength(1);
  expect(panel.get('.admin-notification-incident-meta').text()).toContain('Đã xử lý');
  await panel.get('.admin-notification-actions a').trigger('click');
  await flushPromises();
  expect(router.currentRoute.value.query).toEqual({ tripId: '100' });
});

test('stale notification polls cannot undo a confirmed read or dismissal', async () => {
  user = admin;
  const staleRead = deferred<Response>();
  const staleDelete = deferred<Response>();
  const rows = [alert, { ...alert, id: 2, type: 'REROUTE_CREATED', severity: 'MAJOR' }];
  let loads = 0;
  handlers.set('GET /api/v1/notifications?unreadOnly=false', () => {
    loads += 1;
    if (loads === 2) return staleRead.promise;
    if (loads === 3) return staleDelete.promise;
    return json(rows);
  });
  handlers.set('POST /api/v1/notifications/1/read', () => json({ ...alert, readAt: stamp }));
  handlers.set('DELETE /api/v1/notifications/1', () => new Response(null, { status: 204 }));
  await open('/alerts');
  const panel = notificationPanel();

  await vi.advanceTimersByTimeAsync(15_000);
  await flushPromises();
  await panel.findAll('.admin-notification-card')[0]!.find('button').trigger('click');
  await flushPromises();
  expect(panel.findAll('.admin-notification-card')[0]!.classes()).toContain('read');
  staleRead.resolve(json(rows));
  await flushPromises();
  expect(panel.findAll('.admin-notification-card')[0]!.classes()).toContain('read');

  await vi.advanceTimersByTimeAsync(15_000);
  await flushPromises();
  await panel.findAll('.admin-notification-card')[0]!.find('button').trigger('click');
  await documentView().get('dialog .danger-action').trigger('click');
  await flushPromises();
  expect(panel.findAll('.admin-notification-card')).toHaveLength(1);
  staleDelete.resolve(json(rows));
  await flushPromises();
  expect(panel.findAll('.admin-notification-card')).toHaveLength(1);
  expect(panel.get('.admin-notification-card').text()).toContain('Chuyến #100');
});

test('a newer notification poll wins over an older delayed response', async () => {
  user = admin;
  const delayed = deferred<Response>();
  let loads = 0;
  handlers.set('GET /api/v1/notifications?unreadOnly=false', () => {
    loads += 1;
    return loads === 1 ? delayed.promise : json([{ ...alert, id: 2, title: 'Cảnh báo mới', type: 'REROUTE_CREATED' }]);
  });
  await open('/alerts');
  const panel = notificationPanel();
  await vi.advanceTimersByTimeAsync(15_000);
  await flushPromises();
  expect(calls('/api/v1/notifications?unreadOnly=false')[0]!.options.signal?.aborted).toBe(true);
  expect(panel.findAll('.admin-notification-card')).toHaveLength(1);
  expect(panel.get('.admin-notification-card').text()).toContain('Cảnh báo mới');
  delayed.resolve(json([alert]));
  await flushPromises();
  expect(panel.findAll('.admin-notification-card')).toHaveLength(1);
  expect(panel.get('.admin-notification-card').text()).toContain('Cảnh báo mới');
  expect(panel.get('.admin-notification-card').classes()).toContain('unread');
});

test('alert deletion stays behind confirmation, preserves conflict for retry and only removes acknowledged item', async () => {
  user = admin;
  handlers.set('DELETE /api/v1/notifications/1', () => json({ detail: 'Fixture conflict' }, 409));
  await open('/alerts');
  const panel = notificationPanel();
  await panel.findAll('.admin-notification-card')[0].findAll('button')[1].trigger('click');
  expect(calls('/api/v1/notifications/1', 'DELETE')).toHaveLength(0);
  await documentView().get('dialog .danger-action').trigger('click');
  await flushPromises();
  expect(documentView().get('dialog').text()).toContain('HTTP 409');
  expect(panel.findAll('.admin-notification-card')).toHaveLength(2);
  const pending = deferred<Response>();
  handlers.set('DELETE /api/v1/notifications/1', () => pending.promise);
  await documentView().get('dialog .danger-action').trigger('click');
  await flushPromises();
  await documentView().get('dialog').trigger('cancel');
  expect(documentView().find('dialog').exists()).toBe(true);
  pending.resolve(new Response(null, { status: 204 }));
  await flushPromises();
  expect(documentView().find('dialog').exists()).toBe(false);
  expect(panel.findAll('.admin-notification-card')).toHaveLength(1);
});

test('admin notifications retain one shell polling source across navigation and abort it on unmount', async () => {
  user = admin;
  handlers.set('GET /api/v1/trips', () =>
    json([{ ...trips[0], status: 'IN_PROGRESS', plannedEndAt: '2026-09-22T17:29:59Z' }, trips[1]]),
  );
  handlers.set('GET /api/v1/users', () => json([]));
  const { wrapper, router } = await open('/alerts');
  await vi.advanceTimersByTimeAsync(15_000);
  await flushPromises();
  expect(calls('/api/v1/notifications?unreadOnly=false')).toHaveLength(2);
  const trigger = wrapper.get('.admin-notification-trigger').element;
  await router.push('/dashboard?view=all');
  await flushPromises();
  expect(wrapper.get('.admin-notification-trigger').element).toBe(trigger);
  expect(documentView().find('.admin-notification-panel').exists()).toBe(false);
  expect(calls('/api/v1/notifications?unreadOnly=false')).toHaveLength(2);
  expect(wrapper.findAll('.dashboard-trip-id').map((item) => item.text())).toEqual([
    '#101',
    '#100',
  ]);
  expect(wrapper.get('.dashboard-status.in_progress').text()).toBe('Đang trễ');
  await vi.advanceTimersByTimeAsync(15_000);
  await flushPromises();
  expect(calls('/api/v1/notifications?unreadOnly=false')).toHaveLength(3);
  const summaryRequests = calls('/api/v1/dashboard/summary').length;
  await router.push('/users');
  await flushPromises();
  await vi.advanceTimersByTimeAsync(15_000);
  await flushPromises();
  expect(calls('/api/v1/dashboard/summary')).toHaveLength(summaryRequests);
  expect(calls('/api/v1/notifications?unreadOnly=false')).toHaveLength(4);
  wrapper.unmount();
  expect(calls('/api/v1/dashboard/summary')[0].options.signal?.aborted).toBe(true);
  await vi.advanceTimersByTimeAsync(30_000);
  await flushPromises();
  expect(calls('/api/v1/notifications?unreadOnly=false')[3].options.signal?.aborted).toBe(true);
  expect(calls('/api/v1/dashboard/summary')).toHaveLength(summaryRequests);
  expect(calls('/api/v1/notifications?unreadOnly=false')).toHaveLength(4);
});

test('read-all preserves a notification that arrives while the acknowledgement is pending', async () => {
  user = admin;
  const pending = deferred<Response>();
  handlers.set('POST /api/v1/notifications/read-all', () => pending.promise);
  const { wrapper } = await open('/alerts');
  const panel = notificationPanel();
  await panel.get('.admin-notification-read-all').trigger('click');
  handlers.set('GET /api/v1/notifications?unreadOnly=false', () => json([
    { ...alert, id: 3, title: 'Thông báo mới trong lúc xác nhận' }, alert,
    { ...alert, id: 2, type: 'REROUTE_CREATED', severity: 'MAJOR' },
  ]));
  await vi.advanceTimersByTimeAsync(15_000);
  await flushPromises();
  pending.resolve(json({ updated: 2 }));
  await flushPromises();
  expect(panel.findAll('.admin-notification-card.unread')).toHaveLength(1);
  expect(panel.get('.admin-notification-card.unread').text()).toContain('Thông báo mới trong lúc xác nhận');
  expect(wrapper.get('.admin-notification-badge').text()).toBe('1');
  expect(panel.findAll('.admin-notification-card.read')).toHaveLength(2);
});

test('hidden tabs pause notification polling; failed reads retry without losing the existing list', async () => {
  user = admin;
  const { wrapper } = await open('/alerts');
  const panel = notificationPanel();
  const originalVisibility = Object.getOwnPropertyDescriptor(document, 'visibilityState');
  try {
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'hidden' });
    await vi.advanceTimersByTimeAsync(30_000);
    await flushPromises();
    expect(calls('/api/v1/notifications?unreadOnly=false')).toHaveLength(1);
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' });
    handlers.set('GET /api/v1/notifications?unreadOnly=false', () => json({ detail: 'Fixture unavailable' }, 503));
    await vi.advanceTimersByTimeAsync(15_000);
    await flushPromises();
    expect(panel.get('[role="alert"]').text()).toContain('HTTP 503');
    expect(wrapper.find('.admin-notification-error-dot').exists()).toBe(true);
    expect(panel.findAll('.admin-notification-card')).toHaveLength(2);
    handlers.delete('GET /api/v1/notifications?unreadOnly=false');
    await panel.get('[role="alert"] button').trigger('click');
    await flushPromises();
    expect(panel.find('[role="alert"]').exists()).toBe(false);
    expect(wrapper.find('.admin-notification-error-dot').exists()).toBe(false);
    expect(panel.findAll('.admin-notification-card')).toHaveLength(2);
  } finally {
    if (originalVisibility) Object.defineProperty(document, 'visibilityState', originalVisibility);
    else Reflect.deleteProperty(document, 'visibilityState');
  }
});

test('unmounted admin menu ignores late mutation responses and cleans its Teleport and polling request', async () => {
  user = admin;
  const pending = deferred<Response>();
  handlers.set('POST /api/v1/notifications/1/read', () => pending.promise);
  const { wrapper } = await open('/alerts');
  await notificationPanel().get('.admin-notification-actions button').trigger('click');
  const requestsBeforeUnmount = requests.length;
  wrapper.unmount();
  expect(documentView().find('.admin-notification-panel').exists()).toBe(false);
  expect(calls('/api/v1/notifications?unreadOnly=false')[0].options.signal?.aborted).toBe(true);
  pending.resolve(json({ ...alert, readAt: stamp }));
  await flushPromises();
  await vi.advanceTimersByTimeAsync(30_000);
  expect(documentView().find('.admin-notification-panel').exists()).toBe(false);
  expect(requests).toHaveLength(requestsBeforeUnmount);
});

test('dashboard opens the shared bell, caps a long response at fifty and preserves unrelated query when closing', async () => {
  user = admin;
  handlers.set('GET /api/v1/notifications?unreadOnly=false', () => json(
    Array.from({ length: 60 }, (_, index) => ({ ...alert, id: index + 1, title: `Thông báo ${index + 1}` })),
  ));
  const { wrapper, router } = await open('/dashboard?view=summary');
  expect(documentView().find('.admin-notification-panel').exists()).toBe(false);
  const sourceRequests = calls('/api/v1/notifications?unreadOnly=false').length;
  const openButton = wrapper.findAll('button').find(button => button.text() === 'Mở thông báo');
  expect(openButton).toBeDefined();
  await openButton!.trigger('click');
  await flushPromises();
  expect(notificationPanel().findAll('.admin-notification-card')).toHaveLength(50);
  expect(wrapper.get('.admin-notification-badge').text()).toBe('50');
  expect(calls('/api/v1/notifications?unreadOnly=false')).toHaveLength(sourceRequests);
  await router.push('/dashboard?view=summary&notifications=open');
  await flushPromises();
  await notificationPanel().get('[aria-label="Đóng thông báo admin"]').trigger('click');
  await flushPromises();
  expect(router.currentRoute.value.query).toEqual({ view: 'summary' });
  expect(documentView().find('.admin-notification-panel').exists()).toBe(false);
});

test('a legacy-open menu navigates to the exact revision without its close-query cleanup winning the route change', async () => {
  user = admin;
  handlers.set('GET /api/v1/notifications?unreadOnly=false', () => json([
    { ...alert, type: 'DRIVER_ROUTE_CHANGED', revisionId: 11 },
  ]));
  const { wrapper, router } = await open('/alerts');
  await notificationPanel().get('.admin-notification-actions a').trigger('click');
  await flushPromises();
  expect(router.currentRoute.value.path).toBe('/operations');
  expect(router.currentRoute.value.query).toEqual({ tripId: '100', revisionId: '11' });
  expect(documentView().find('.admin-notification-panel').exists()).toBe(false);
  expect(wrapper.find('[data-workflow-map-fixture]').exists()).toBe(true);
  expect(calls('/api/v1/notifications?unreadOnly=false')).toHaveLength(1);
});

test('route navigation closes an outstanding delete confirmation without acknowledging the resource', async () => {
  user = admin;
  handlers.set('GET /api/v1/users', () => json([]));
  const { router } = await open('/alerts');
  await notificationPanel().findAll('.admin-notification-card')[0].findAll('button')[1].trigger('click');
  expect(documentView().get('dialog').attributes('open')).toBeDefined();
  await router.push('/users');
  await flushPromises();
  expect(documentView().find('dialog').exists()).toBe(false);
  expect(documentView().find('.admin-notification-panel').exists()).toBe(false);
  expect(calls('/api/v1/notifications/1', 'DELETE')).toHaveLength(0);
  expect(calls('/api/v1/notifications?unreadOnly=false')).toHaveLength(1);
});

test('operations alert stream describes simulator notification categories', () => {
  const wrapper = mount(AlertStream, { props: { notifications: [] } });
  expect(wrapper.get('.alerts-empty p').text()).toContain('đổi tuyến và điều phối');
  expect(wrapper.get('.alert-legend').text()).toContain('Đổi tuyến');
  expect(wrapper.get('.alert-legend').text()).toContain('Điều phối');
  expect(wrapper.get('.alert-legend').text()).not.toContain('Lệch tuyến');
  wrapper.unmount();
});
