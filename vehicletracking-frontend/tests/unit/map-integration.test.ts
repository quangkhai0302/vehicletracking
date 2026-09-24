import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import L from 'leaflet';
import MapComponent from '@/features/map/components/MapComponent.vue';
import * as operations from '@/features/tracking/api/operations';
import * as fleetApi from '@/features/fleet/api/fleet';
import type { OperationsSnapshot } from '@/features/tracking/types/operations';
import type { TripDetail } from '@/features/fleet/types/fleet';
vi.mock('@/features/tracking/api/operations', () => ({ fetchOperations: vi.fn(), subscribeOperations: vi.fn(), controlSimulation: vi.fn() }));
const snapshot: OperationsSnapshot = { serverTime: '2026-09-23T01:00:00Z', positions: [], trips: [], simulations: [], checkIns: [], notifications: [] };
const originalCanvas = Object.getOwnPropertyDescriptor(L.Browser, 'canvas')!, originalSvg = Object.getOwnPropertyDescriptor(L.Browser, 'svg')!;
const originalCheckVisibility = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'checkVisibility');
const unmounts: (() => void)[] = [];
beforeEach(() => {
  vi.resetAllMocks(); vi.mocked(operations.fetchOperations).mockResolvedValue(snapshot); vi.mocked(operations.subscribeOperations).mockImplementation(callback => { callback(snapshot); return vi.fn(); });
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input); const body = url.includes('/traffic/') ? { source: 'HERE_LIVE', status: 'AVAILABLE', observedAt: snapshot.serverTime, fetchedAt: snapshot.serverTime, ageSeconds: 0, warning: null, results: [] } : [];
    return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
  }));
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() })));
  vi.stubGlobal('ResizeObserver', class { observe = vi.fn(); disconnect = vi.fn(); });
  // jsdom has no layout/visibility implementation; model the hidden panel contract.
  Object.defineProperty(HTMLElement.prototype, 'checkVisibility', { configurable: true, value(this: HTMLElement) { return !this.closest('[hidden]'); } });
  Object.defineProperty(L.Browser, 'canvas', { ...originalCanvas, value: false }); Object.defineProperty(L.Browser, 'svg', { ...originalSvg, value: true });
});
afterEach(() => {
  unmounts.splice(0).forEach(unmount => unmount()); document.body.replaceChildren(); vi.unstubAllGlobals(); vi.restoreAllMocks();
  Object.defineProperty(L.Browser, 'canvas', originalCanvas); Object.defineProperty(L.Browser, 'svg', originalSvg);
  if (originalCheckVisibility) Object.defineProperty(HTMLElement.prototype, 'checkVisibility', originalCheckVisibility);
  else Reflect.deleteProperty(HTMLElement.prototype, 'checkVisibility');
});
test('map workspace mode changes preserve the Leaflet owner and its SSE subscription', async () => {
  const remove = vi.spyOn(L.Map.prototype, 'remove');
  const wrapper = mount(MapComponent, { props: { initialWorkspace: 'tracking', embedded: true }, attachTo: document.body }); unmounts.push(() => wrapper.unmount());
  await flushPromises(); const canvas = wrapper.get('#main-map').element;
  await wrapper.setProps({ initialWorkspace: 'simulation' }); await flushPromises();
  await wrapper.setProps({ initialWorkspace: 'tracking' }); await flushPromises();
  expect(wrapper.get('#main-map').element).toBe(canvas); expect(wrapper.attributes('data-workspace')).toBe('tracking');
  expect(operations.subscribeOperations).toHaveBeenCalledTimes(1); expect(remove).not.toHaveBeenCalled();
  wrapper.unmount(); expect(remove).toHaveBeenCalledTimes(1); expect(vi.mocked(operations.subscribeOperations).mock.results[0].value).toHaveBeenCalledTimes(1);
});
test('twenty full map mounts dispose each map and realtime subscription', async () => {
  const remove = vi.spyOn(L.Map.prototype, 'remove');
  for (let index = 0; index < 20; index++) {
    const wrapper = mount(MapComponent, { attachTo: document.body }); await flushPromises(); wrapper.unmount(); await flushPromises();
    expect(remove).toHaveBeenCalledTimes(index + 1); expect(vi.mocked(operations.subscribeOperations).mock.results[index].value).toHaveBeenCalledTimes(1);
    expect(document.querySelector('.leaflet-container')).toBeNull();
  }
});

test('changing basemap keeps one tile layer and clears a failed-tile retry and its listeners', async () => {
  const createMap = vi.spyOn(L, 'map'), timeout = vi.spyOn(globalThis, 'setTimeout'), clear = vi.spyOn(globalThis, 'clearTimeout');
  const wrapper = mount(MapComponent, { attachTo: document.body }); unmounts.push(() => wrapper.unmount()); await flushPromises();
  const map: L.Map = createMap.mock.results[0].value;
  const tiles = () => { const result: L.TileLayer[] = []; map.eachLayer(layer => { if (layer instanceof L.TileLayer) result.push(layer); }); return result; };
  expect(tiles()).toHaveLength(1); const old = tiles()[0];
  const image = document.createElement('img'); image.src = 'https://fixture.invalid/tile';
  old.fire('tileerror', { tile: image }); const retryIndex = timeout.mock.calls.findIndex(call => call[1] === 600);
  expect(retryIndex).toBeGreaterThanOrEqual(0); const timer = timeout.mock.results[retryIndex].value;
  await wrapper.get('.gm-basemap-card:nth-child(3)').trigger('click'); await flushPromises();
  expect(tiles()).toHaveLength(1); expect(tiles()[0]).not.toBe(old); expect(tiles()[0].options.className).toBe('dark-map-tiles');
  expect(clear).toHaveBeenCalledWith(timer); expect(old.listens('tileerror')).toBe(false); expect(old.listens('tileload')).toBe(false);
  expect(createMap).toHaveBeenCalledTimes(1); expect(operations.subscribeOperations).toHaveBeenCalledTimes(1);
});


test('selecting a scheduled vehicle opens its trip summary without presenting the planned marker as GPS', async () => {
  const trip = {
    id: 42, vehicleId: 7, vehiclePlateNumber: '63B853904', vehicleType: 'CAR' as const,
    routeId: 3, routeName: 'Bến Thành → Suối Tiên', status: 'SCHEDULED' as const,
    scheduledDepartureAt: snapshot.serverTime, plannedEndAt: snapshot.serverTime,
    startedAt: null, endedAt: null, createdAt: snapshot.serverTime, driver: null,
  };
  const detail = {
    trip,
    stops: [{ sequenceNumber: 1, stationName: 'Bến Thành', latitude: 10.77, longitude: 106.7 }],
    route: { stops: [], sections: [], shapingPoints: [] },
  } as unknown as TripDetail;
  const plannedSnapshot = { ...snapshot, trips: [trip] };
  vi.mocked(operations.fetchOperations).mockResolvedValue(plannedSnapshot);
  vi.mocked(operations.subscribeOperations).mockImplementation(callback => {
    callback(plannedSnapshot);
    return vi.fn();
  });
  vi.spyOn(fleetApi, 'fetchTrip').mockResolvedValue(detail);
  vi.spyOn(fleetApi, 'fetchTripRoute').mockResolvedValue(detail.route);

  const wrapper = mount(MapComponent, { props: { initialTripId: 42 }, attachTo: document.body });
  unmounts.push(() => wrapper.unmount());
  await flushPromises();

  expect(wrapper.get('.context-drawer').attributes('hidden')).toBeUndefined();
  expect(wrapper.get('.tracking-vehicle-card').text()).toContain('63B853904');
  expect(wrapper.get('.tracking-vehicle-card').text()).toContain('không phải vị trí thực tế');
  expect(wrapper.get('.live-follow').attributes('hidden')).toBeDefined();
  await wrapper.get('.tracking-vehicle-clear').trigger('click');
  expect(wrapper.get('.context-drawer').attributes('hidden')).toBeDefined();
});

test('failed basemap tiles show a retry action that replaces the tile layer', async () => {
  const createMap = vi.spyOn(L, 'map');
  const wrapper = mount(MapComponent, { attachTo: document.body });
  unmounts.push(() => wrapper.unmount());
  await flushPromises();
  const map: L.Map = createMap.mock.results[0].value;
  const tiles = () => {
    const result: L.TileLayer[] = [];
    map.eachLayer(layer => { if (layer instanceof L.TileLayer) result.push(layer); });
    return result;
  };
  const old = tiles()[0];
  old.fire('loading');
  old.fire('tileerror', { tile: document.createElement('img') });
  old.fire('load');
  await flushPromises();
  expect(wrapper.get('.basemap-error').text()).toContain('Không tải được bản đồ nền');

  await wrapper.get('.basemap-error button').trigger('click');
  await flushPromises();
  expect(tiles()).toHaveLength(1);
  expect(tiles()[0]).not.toBe(old);
  expect(wrapper.find('.basemap-error').exists()).toBe(false);
});
