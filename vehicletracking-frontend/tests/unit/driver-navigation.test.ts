import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { effectScope, nextTick, ref } from 'vue';
import { flushPromises, mount } from '@vue/test-utils';
import * as api from '@/features/fleet/api/driverPortal';
import * as incidentApi from '@/features/simulation/api/incidents';
import { useDriverNavigation } from '@/features/fleet/composables/useDriverNavigation';
import DriverNavigation from '@/features/fleet/components/DriverNavigation.vue';
import type { DriverNavigationSnapshot } from '@/features/fleet/types/driverNavigation';
import { driverOptions, driverSnapshot, stamp } from './fixtures/driverNavigation';

vi.mock('@/features/fleet/api/driverPortal', () => ({ fetchDriverNavigation: vi.fn(), startDriverTrip: vi.fn(), fetchDriverRouteOptions: vi.fn(), applyDriverRouteOption: vi.fn() }));
vi.mock('@/features/simulation/api/incidents', () => ({ reportDriverSimulationIncident: vi.fn() }));
vi.mock('@/shared/composables/useErrorToast', () => ({ useErrorToast: vi.fn() }));
const scopes: ReturnType<typeof effectScope>[] = [];
const unmounts: (() => void)[] = [];
function setup(id = ref(7)) {
  const scope = effectScope(); scopes.push(scope);
  return { id, state: scope.run(() => useDriverNavigation(id))! };
}
function deferred() {
  let resolve!: (data: DriverNavigationSnapshot) => void;
  const promise = new Promise<DriverNavigationSnapshot>(yes => { resolve = yes; });
  return { promise, resolve };
}
beforeEach(() => {
  vi.resetAllMocks(); vi.useFakeTimers(); vi.setSystemTime(new Date(stamp));
  vi.mocked(api.fetchDriverNavigation).mockResolvedValue(driverSnapshot());
  vi.mocked(api.startDriverTrip).mockResolvedValue(driverSnapshot('IN_PROGRESS'));
  vi.mocked(api.fetchDriverRouteOptions).mockResolvedValue(driverOptions());
  vi.mocked(api.applyDriverRouteOption).mockResolvedValue(driverSnapshot('IN_PROGRESS', 17));
  const incidentSnapshot = driverSnapshot('IN_PROGRESS');
  incidentSnapshot.simulation!.status = 'PAUSED';
  vi.mocked(incidentApi.reportDriverSimulationIncident).mockResolvedValue({
    id: 1, tripId: 7, vehicleId: 1, vehiclePlateNumber: '51B-12345', reportedByDriverId: 9,
    reportedByDriverName: 'Tài xế thử nghiệm', attemptNumber: 1, type: 'VEHICLE_BREAKDOWN', severity: 'MAJOR',
    status: 'OPEN', detail: null, latitude: 10.77, longitude: 106.7, simulatedElapsedSeconds: 5,
    createdAt: stamp, acknowledgedAt: null, resolvedAt: null, simulation: incidentSnapshot.simulation,
  });
});
afterEach(() => { unmounts.splice(0).forEach(fn => fn()); scopes.splice(0).forEach(s => s.stop()); vi.clearAllTimers(); vi.useRealTimers(); });

test('loads only assigned trip and stops polling/timers when disposed', async () => {
  const { state } = setup(); await flushPromises();
  expect(api.fetchDriverNavigation).toHaveBeenCalledWith(7, expect.any(AbortSignal));
  expect(state.connected).toBe(true);
  scopes[0]!.stop(); expect(vi.getTimerCount()).toBe(0);
  await vi.advanceTimersByTimeAsync(3000); expect(api.fetchDriverNavigation).toHaveBeenCalledTimes(1);
});
test('does not overlap navigation reads', async () => {
  const pending = deferred(); vi.mocked(api.fetchDriverNavigation).mockReturnValue(pending.promise);
  setup(); await vi.advanceTimersByTimeAsync(3500);
  expect(api.fetchDriverNavigation).toHaveBeenCalledTimes(1);
  pending.resolve(driverSnapshot()); await flushPromises();
  await vi.advanceTimersByTimeAsync(1000); expect(api.fetchDriverNavigation).toHaveBeenCalledTimes(2);
});
test('start prevents double submit and adopts the running response', async () => {
  const pending = deferred(); vi.mocked(api.startDriverTrip).mockReturnValue(pending.promise);
  const { state } = setup(); await flushPromises();
  const request = state.start(); await state.start();
  expect(state.busy).toBe(true); expect(api.startDriverTrip).toHaveBeenCalledTimes(1);
  vi.mocked(api.fetchDriverNavigation).mockResolvedValue(driverSnapshot('IN_PROGRESS'));
  pending.resolve(driverSnapshot('IN_PROGRESS')); await request; await flushPromises();
  expect(state.snapshot?.trip.status).toBe('IN_PROGRESS'); expect(state.busy).toBe(false);
});
test('does not let a late pre-start read overwrite the start response', async () => {
  const { state } = setup(); await flushPromises();
  const late = deferred(); vi.mocked(api.fetchDriverNavigation).mockReturnValueOnce(late.promise);
  await vi.advanceTimersByTimeAsync(1000);
  const calls = vi.mocked(api.fetchDriverNavigation).mock.calls;
  const aborted = calls[calls.length - 1]![1]!;
  vi.mocked(api.fetchDriverNavigation).mockResolvedValue(driverSnapshot('IN_PROGRESS'));
  await state.start(); late.resolve(driverSnapshot()); await flushPromises();
  expect(aborted.aborted).toBe(true); expect(state.snapshot?.trip.status).toBe('IN_PROGRESS');
});
test('does not resume an admin-paused run or request routes for it', async () => {
  const paused = driverSnapshot('IN_PROGRESS'); paused.simulation!.status = 'PAUSED';
  vi.mocked(api.fetchDriverNavigation).mockResolvedValue(paused);
  const { state } = setup(); await flushPromises(); await state.start(); await state.loadOptions();
  expect(state.canChange).toBe(false); expect(api.startDriverTrip).not.toHaveBeenCalled(); expect(api.fetchDriverRouteOptions).not.toHaveBeenCalled();
});
test('driver reports an incident on the assigned active trip with a stable idempotency key', async () => {
  const paused = driverSnapshot('IN_PROGRESS'); paused.simulation!.status = 'PAUSED';
  vi.mocked(api.fetchDriverNavigation).mockResolvedValue(driverSnapshot('IN_PROGRESS'));
  const { state } = setup(); await flushPromises();
  vi.mocked(api.fetchDriverNavigation).mockResolvedValue(paused);
  const report = {
    type: 'VEHICLE_BREAKDOWN' as const, severity: 'MAJOR' as const, detail: 'Xe bị hỏng',
    idempotencyKey: 'same-request-on-retry',
  };
  expect(await state.reportIncident(report)).toBe(true);
  expect(incidentApi.reportDriverSimulationIncident).toHaveBeenCalledWith(7, expect.objectContaining({
    ...report, attemptNumber: 1,
  }), expect.any(AbortSignal));
  expect(state.snapshot?.simulation?.status).toBe('PAUSED');
});
test('preview is local until explicit confirmation and then adopts the shared revision', async () => {
  vi.mocked(api.fetchDriverNavigation).mockResolvedValue(driverSnapshot('IN_PROGRESS'));
  const { state } = setup(); await flushPromises(); await state.loadOptions(); await flushPromises();
  expect(state.options?.options).toHaveLength(2); expect(state.snapshot?.routeRevisionId).toBeNull();
  expect(api.applyDriverRouteOption).not.toHaveBeenCalled(); state.selectedIndex = 1;
  vi.mocked(api.fetchDriverNavigation).mockResolvedValue(driverSnapshot('IN_PROGRESS', 17));
  await state.apply(); await flushPromises();
  expect(api.applyDriverRouteOption).toHaveBeenCalledWith(7, 'preview-token', 1, expect.any(AbortSignal));
  expect(state.snapshot?.routeRevisionId).toBe(17); expect(state.options).toBeNull();
});
test('new official revision invalidates an uncommitted preview', async () => {
  vi.mocked(api.fetchDriverNavigation).mockResolvedValue(driverSnapshot('IN_PROGRESS'));
  const { state } = setup(); await flushPromises(); await state.loadOptions(); await flushPromises();
  vi.mocked(api.fetchDriverNavigation).mockResolvedValue(driverSnapshot('IN_PROGRESS', 22));
  await vi.advanceTimersByTimeAsync(1000);
  expect(state.snapshot?.routeRevisionId).toBe(22); expect(state.options).toBeNull(); expect(state.selectedIndex).toBeNull();
});
test('expired preview cannot be applied', async () => {
  vi.mocked(api.fetchDriverNavigation).mockResolvedValue(driverSnapshot('IN_PROGRESS'));
  vi.mocked(api.fetchDriverRouteOptions).mockResolvedValue({ ...driverOptions(), expiresAt: '2026-09-29T01:59:59Z' });
  const { state } = setup(); await flushPromises(); await state.loadOptions(); await flushPromises(); await state.apply();
  expect(state.optionsExpired).toBe(true); expect(api.applyDriverRouteOption).not.toHaveBeenCalled();
});
test('switching trips aborts old responses and cleanup aborts mutations', async () => {
  const pending = deferred(); vi.mocked(api.fetchDriverNavigation).mockReturnValueOnce(pending.promise);
  const { id, state } = setup(); const oldSignal = vi.mocked(api.fetchDriverNavigation).mock.calls[0]![1]!;
  vi.mocked(api.fetchDriverNavigation).mockResolvedValue(driverSnapshot('SCHEDULED', null, 8));
  id.value = 8; await nextTick(); await flushPromises(); pending.resolve(driverSnapshot()); await flushPromises();
  expect(oldSignal.aborted).toBe(true); expect(state.snapshot?.trip.id).toBe(8);
  const action = deferred(); vi.mocked(api.startDriverTrip).mockReturnValue(action.promise);
  const request = state.start(); const signal = vi.mocked(api.startDriverTrip).mock.calls[0]![1]!;
  scopes[0]!.stop(); expect(signal.aborted).toBe(true);
  action.resolve(driverSnapshot('IN_PROGRESS', null, 8)); await request; expect(vi.getTimerCount()).toBe(0);
});
test('connection errors keep the last route but disable changes', async () => {
  vi.mocked(api.fetchDriverNavigation).mockResolvedValue(driverSnapshot('IN_PROGRESS'));
  const { state } = setup(); await flushPromises();
  vi.mocked(api.fetchDriverNavigation).mockRejectedValue(new Error('Mất kết nối'));
  await vi.advanceTimersByTimeAsync(1000); await state.loadOptions();
  expect(state.snapshot?.trip.id).toBe(7); expect(state.connected).toBe(false); expect(state.error).toBe('Mất kết nối');
  expect(api.fetchDriverRouteOptions).not.toHaveBeenCalled();
});
test('provider/apply failure preserves official geometry and permits retry', async () => {
  vi.mocked(api.fetchDriverNavigation).mockResolvedValue(driverSnapshot('IN_PROGRESS'));
  const { state } = setup(); await flushPromises(); await state.loadOptions(); await flushPromises();
  vi.mocked(api.applyDriverRouteOption).mockRejectedValue(new Error('Xe đã đi khỏi điểm chọn đường'));
  await state.apply(); await flushPromises();
  expect(state.snapshot?.routeRevisionId).toBeNull(); expect(state.options).toBeNull(); expect(state.busy).toBe(false);
  expect(state.error).toContain('Xe đã đi'); expect(state.canChange).toBe(true);
});
function component() {
  const wrapper = mount(DriverNavigation, { props: { tripId: 7 }, global: { stubs: { DriverNavigationMap: true, RouterLink: { template: '<a><slot /></a>' } } } });
  unmounts.push(() => wrapper.unmount()); return wrapper;
}
test.each(['ON_DEMAND', 'FIXED_SCHEDULE'] as const)('driver map supports %s manual start and passes official route to map', async (dispatchMode) => {
  const snapshot = driverSnapshot();
  snapshot.trip.dispatchMode = dispatchMode;
  vi.mocked(api.fetchDriverNavigation).mockResolvedValue(snapshot);
  const wrapper = component(); await flushPromises();
  expect(wrapper.text()).toContain('Bắt đầu chuyến'); expect(wrapper.text()).toContain('Mô phỏng');
  expect(wrapper.find('.driver-incident-report-button').exists()).toBe(false);
  expect(wrapper.findComponent({ name: 'DriverNavigationMap' }).props('snapshot').route).toEqual(driverSnapshot().route);
  const stopLabels = wrapper.findAll('.driver-trip-stop-state').map(el => el.text());
  expect(stopLabels).toEqual(['Trạm đầu', 'Trạm cuối']);
  expect(wrapper.text()).not.toContain('xác nhận Sẵn sàng');
  await wrapper.get('button.driver-navigation-primary').trigger('click');
  await flushPromises();
  expect(api.startDriverTrip).toHaveBeenCalledWith(7, expect.any(AbortSignal));
});
test('driver can view suggestions, choose a route and explicitly confirm', async () => {
  vi.mocked(api.fetchDriverNavigation).mockResolvedValue(driverSnapshot('IN_PROGRESS'));
  const wrapper = component(); await flushPromises();
  expect(wrapper.text()).toContain('Rẽ phải vào đường thử nghiệm');
  await wrapper.find('button.driver-navigation-primary').trigger('click'); await flushPromises();
  expect(wrapper.text()).toContain('đang xem thử');
  const radios = wrapper.findAll('input[type="radio"]'); await radios[1]!.setValue(true);
  expect(api.applyDriverRouteOption).not.toHaveBeenCalled();
  vi.mocked(api.fetchDriverNavigation).mockResolvedValue(driverSnapshot('IN_PROGRESS', 17));
  await wrapper.find('button.driver-navigation-primary').trigger('click'); await flushPromises();
  expect(wrapper.text()).toContain('Lộ trình #17'); expect(wrapper.find('input[type="radio"]').exists()).toBe(false);
});
test('legacy routes clearly explain missing turn instructions', async () => {
  const legacy = driverSnapshot('IN_PROGRESS'); legacy.route.sections[0]!.instructions = []; legacy.guidance = null;
  vi.mocked(api.fetchDriverNavigation).mockResolvedValue(legacy);
  const wrapper = component(); await flushPromises();
  expect(wrapper.text()).toContain('chưa có chỉ dẫn rẽ từng bước'); expect(wrapper.text()).toContain('Theo lộ trình đến Trạm 2');
});
test('shows scoped speed, progress, station ETA and actual check-in counts', async () => {
  const data = driverSnapshot('IN_PROGRESS');
  data.simulation!.frame!.progressPercent = 42; data.simulation!.frame!.nextStopEtaSeconds = 75;
  vi.mocked(api.fetchDriverNavigation).mockResolvedValue(data);
  const wrapper = component(); await flushPromises();
  expect(wrapper.find('.driver-incident-report-button').exists()).toBe(true);
  expect(wrapper.text()).toContain('20 km/h'); expect(wrapper.text()).toContain('1p 15s');
  expect(wrapper.find('[role="progressbar"]').attributes('aria-valuenow')).toBe('42');
  expect(wrapper.text()).toContain('0/2 trạm đã check-in');
  expect(wrapper.findAll('.driver-trip-stops li').map(x => x.attributes('data-state'))).toEqual(['pending', 'next']);
});

test('opens incident reporting without randomUUID and reuses the request key when retrying', async () => {
  const webCrypto = globalThis.crypto;
  const showModal = Object.getOwnPropertyDescriptor(HTMLDialogElement.prototype, 'showModal');
  const close = Object.getOwnPropertyDescriptor(HTMLDialogElement.prototype, 'close');
  vi.stubGlobal('crypto', { getRandomValues: (bytes: Uint8Array<ArrayBuffer>) => webCrypto.getRandomValues(bytes) });
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', { configurable: true, value() {} });
  Object.defineProperty(HTMLDialogElement.prototype, 'close', { configurable: true, value() {} });
  try {
    vi.mocked(api.fetchDriverNavigation).mockResolvedValue(driverSnapshot('IN_PROGRESS'));
    vi.mocked(incidentApi.reportDriverSimulationIncident).mockRejectedValueOnce(new Error('Mất kết nối'));
    const wrapper = component(); await flushPromises();
    await wrapper.get('.driver-incident-report-button').trigger('click');
    expect(wrapper.get('dialog h2').text()).toBe('Ghi nhận sự cố mô phỏng');
    await wrapper.get('dialog textarea').setValue('Xe cần kiểm tra kỹ thuật');
    await wrapper.get('dialog form').trigger('submit'); await flushPromises();
    expect(wrapper.find('dialog').exists()).toBe(true);
    const firstKey = vi.mocked(incidentApi.reportDriverSimulationIncident).mock.calls[0]![1].idempotencyKey;
    expect(firstKey).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
    await wrapper.get('dialog form').trigger('submit'); await flushPromises();
    expect(incidentApi.reportDriverSimulationIncident).toHaveBeenLastCalledWith(7, expect.objectContaining({
      idempotencyKey: firstKey, detail: 'Xe cần kiểm tra kỹ thuật', type: 'VEHICLE_BREAKDOWN', severity: 'MAJOR', attemptNumber: 1,
    }), expect.any(AbortSignal));
    expect(wrapper.find('dialog').exists()).toBe(false);
  } finally {
    vi.unstubAllGlobals();
    if (showModal) Object.defineProperty(HTMLDialogElement.prototype, 'showModal', showModal);
    else Reflect.deleteProperty(HTMLDialogElement.prototype, 'showModal');
    if (close) Object.defineProperty(HTMLDialogElement.prototype, 'close', close);
    else Reflect.deleteProperty(HTMLDialogElement.prototype, 'close');
  }
});
