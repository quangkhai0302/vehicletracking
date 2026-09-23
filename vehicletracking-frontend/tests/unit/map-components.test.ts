import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { effectScope, nextTick, reactive, shallowRef } from 'vue';
import L from 'leaflet';
import MapControls from '@/features/map/components/MapControls.vue';
import MapInspectionCard from '@/features/traffic/components/MapInspectionCard.vue';
import SimulationFleetLayer from '@/features/simulation/components/SimulationFleetLayer.vue';
import SimulationRoutesLayer from '@/features/simulation/components/SimulationRoutesLayer.vue';
import TrafficLayer from '@/features/traffic/components/TrafficLayer.vue';
import SimulatorPanel from '@/features/simulation/components/SimulatorPanel.vue';
import SortableStopList from '@/features/routes/components/SortableStopList.vue';
import StationPanel from '@/features/stations/components/StationPanel.vue';
import StationDrawer from '@/features/stations/components/StationDrawer.vue';
import RouteShapeEditor from '@/features/routes/components/RouteShapeEditor.vue';
import { shapeRoute } from '@/features/routes/api/routes';
import { useMapCamera } from '@/features/map/composables/useMapCamera';
import type { useSimulator } from '@/features/simulation/composables/useSimulator';
import type { WaitingSimulationVehicle } from '@/features/simulation/composables/useSimulationFleet';
import type { TripDetail, TripSummary } from '@/features/fleet/types/fleet';
import type { OperationsSnapshot } from '@/features/tracking/types/operations';
import type { TrafficIncidentsResponse } from '@/features/traffic/types/traffic';
import type { RouteDraftStop } from '@/features/routes/types/route';
import type { Station, StationFormState } from '@/features/stations/types/station';

vi.mock('@/features/routes/api/routes', () => ({ shapeRoute: vi.fn() }));

const stamp = '2026-09-22T01:00:00Z';
const trip: TripSummary = { id: 1, attemptNumber: 1, vehicleId: 1, vehiclePlateNumber: '51B00001', vehicleType: 'CAR', routeId: 1, routeName: 'Fixture route', scheduledDepartureAt: stamp,
  plannedEndAt: stamp, startedAt: null, endedAt: null, createdAt: stamp, driver: null, status: 'SCHEDULED' };
const route: TripDetail['route'] = { id: 1, name: 'Fixture route', transportMode: 'CAR', routingProvider: 'HERE', totalDistanceMeters: 1000,
  estimatedTravelDurationSeconds: 600, baseTravelDurationSeconds: 600, totalDwellDurationSeconds: 60, estimatedTripDurationSeconds: 660,
  estimatedDepartureAt: stamp, calculatedAt: stamp, createdAt: stamp, stops: [], sections: [] };
const start: WaitingSimulationVehicle['start'] = { stationId: 1, stationName: 'First stop', sequenceNumber: 1, latitude: 10.8, longitude: 106.7, checkinRadiusMeters: 100,
  dwellDurationSeconds: 60, arrivalOffsetSeconds: 0, departureOffsetSeconds: 60, plannedArrivalAt: stamp, plannedDepartureAt: stamp };
const snapshot: OperationsSnapshot = { serverTime: stamp, trips: [trip], simulations: [], positions: [], notifications: [], checkIns: [] };
const maps: L.Map[] = [], disposals: (() => void)[] = [];
function makeMap() { const host = document.createElement('div'); document.body.append(host); const map = L.map(host).setView([10.8, 106.7], 14); maps.push(map); return map; }
function layers(map: L.Map) { const result: L.Layer[] = []; map.eachLayer(layer => result.push(layer)); return result; }
const originalSvg = Object.getOwnPropertyDescriptor(L.Browser, 'svg')!;
beforeEach(() => { vi.mocked(shapeRoute).mockReset(); Object.defineProperty(L.Browser, 'svg', { ...originalSvg, value: true }); });
afterEach(() => {
  disposals.splice(0).forEach(dispose => dispose()); maps.splice(0).forEach(map => map.remove()); document.body.replaceChildren();
  Object.defineProperty(L.Browser, 'svg', originalSvg); vi.restoreAllMocks(); vi.unstubAllGlobals();
});

test('waiting vehicle layer preserves markers for identical geometry and releases popup DOM handlers', async () => {
  const map = makeMap(), vehicles = [{ trip, start }, { trip: { ...trip, id: 2, vehicleId: 2 }, start }], select = vi.fn();
  const wrapper = mount(SimulationFleetLayer, { props: { map, mapReady: true, visible: true, vehicles, onSelect: select } }); disposals.push(() => wrapper.unmount());
  const marker = layers(map).find(layer => layer instanceof L.Marker) as L.Marker;
  const popup = marker.getPopup()!.getContent() as HTMLElement, button = popup.querySelector('button')!;
  expect(marker.getElement()?.querySelector('[data-waiting-count="2"]')).not.toBeNull();
  await wrapper.setProps({ vehicles: vehicles.map(row => ({ ...row, trip: { ...row.trip, status: 'IN_PROGRESS' } })) });
  expect(layers(map).find(layer => layer instanceof L.Marker)).toBe(marker);
  button.click(); expect(select).toHaveBeenCalledWith(1);
  await wrapper.setProps({ visible: false }); expect(layers(map)).toEqual([]); expect(button.onclick).toBeNull(); expect(marker.listens('click')).toBe(false);
});

test('twenty route-layer mounts/unmounts leave no layers or keyboard handlers behind', () => {
  const map = makeMap(), select = vi.fn();
  const routes = [{ trip, route, segments: [[[10.8, 106.7], [10.81, 106.71]]] as [number, number][][] },
    { trip: { ...trip, id: 2, vehicleId: 2 }, route, segments: [[[10.82, 106.72], [10.83, 106.73]]] as [number, number][][] }];
  for (let i = 0; i < 20; i++) {
    const wrapper = mount(SimulationRoutesLayer, { props: { map, mapReady: true, visible: true, routes, selectedTripId: 1, onSelect: select } });
    const paths = map.getContainer().querySelectorAll<SVGElement>('[data-simulation-route-trip]'); expect(paths).toHaveLength(1);
    expect(paths[0].dataset.simulationRouteTrip).toBe('1'); paths[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    expect(select).toHaveBeenCalledTimes(i + 1); wrapper.unmount(); expect(layers(map)).toEqual([]);
    paths[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' })); expect(select).toHaveBeenCalledTimes(i + 1);
  }
});

test('traffic layer treats incident text as text and releases markers when hidden', async () => {
  const map = makeMap();
  const incidents: TrafficIncidentsResponse = { source: 'HERE_LIVE', status: 'AVAILABLE', observedAt: stamp, fetchedAt: stamp, ageSeconds: 0, warning: null,
    results: [{ id: 'fixture', description: '<img src=x onerror=alert(1)>', type: 'closure', criticality: 'critical', center: [10.8, 106.7], points: [] }] };
  const wrapper = mount(TrafficLayer, { props: { map, visible: true, mapReady: true, incidents } }); disposals.push(() => wrapper.unmount());
  const marker = layers(map).find(layer => layer instanceof L.Marker) as L.Marker;
  const content = marker.getPopup()!.getContent() as HTMLElement;
  expect(content.querySelector('img')).toBeNull(); expect(content.textContent).toContain('<img src=x onerror=alert(1)>');
  await wrapper.setProps({ visible: false }); expect(layers(map)).toEqual([]); expect(marker.listens('click')).toBe(false);
});

test('map controls keep theme/toggle callbacks and close details with Escape/outside click', async () => {
  const change = vi.fn(), toggle = vi.fn();
  const props = { theme: 'google-roadmap' as const, onThemeChange: change, onResetCenter: vi.fn(), onZoomIn: vi.fn(), onZoomOut: vi.fn(), onFit: vi.fn(), canFit: true,
    showStations: true, showRoutes: true, onToggleStations: toggle, onToggleRoutes: vi.fn(), showTraffic: true, onToggleTraffic: vi.fn(), trafficMessage: 'Fixture traffic', trafficCanRetry: false, onRetryTraffic: vi.fn() };
  const wrapper = mount(MapControls, { props, attachTo: document.body }); disposals.push(() => wrapper.unmount());
  await wrapper.findAll('.gm-basemap-card')[2].trigger('click'); expect(change).toHaveBeenCalledWith('google-dark');
  await wrapper.findAll('input')[0].setValue(false); expect(toggle).toHaveBeenCalledTimes(1);
  const details = wrapper.get('details').element as HTMLDetailsElement; details.open = true;
  await wrapper.get('details').trigger('keydown', { key: 'Escape' }); expect(details.open).toBe(false); expect(document.activeElement).toBe(wrapper.get('summary').element);
  details.open = true; document.body.dispatchEvent(new MouseEvent('pointerdown', { bubbles: true })); expect(details.open).toBe(false);
});

test('inspection card teleports without wrapper, stays in viewport, and disposes ResizeObserver', async () => {
  const observe = vi.fn(), disconnect = vi.fn();
  vi.stubGlobal('ResizeObserver', class { observe = observe; disconnect = disconnect; });
  const close = vi.fn();
  const wrapper = mount(MapInspectionCard, { props: { x: window.innerWidth - 1, y: window.innerHeight - 1, pinned: true, kind: 'route', onClose: close }, slots: { default: '<strong>Fixture route</strong>' } });
  const card = document.body.querySelector<HTMLElement>('.route-inspection-card')!;
  expect(card.parentElement).toBe(document.body); expect(card.getAttribute('role')).toBe('dialog');
  expect(parseFloat(card.style.left) + 304).toBeLessThanOrEqual(window.innerWidth - 8);
  expect(parseFloat(card.style.top) + 360).toBeLessThanOrEqual(window.innerHeight - 8);
  card.querySelector('button')!.click(); expect(close).toHaveBeenCalledTimes(1);
  await wrapper.setProps({ pinned: false }); expect(card.getAttribute('role')).toBe('tooltip'); expect(card.querySelector('button')).toBeNull();
  wrapper.unmount(); expect(document.body.querySelector('.route-inspection-card')).toBeNull(); expect(disconnect).toHaveBeenCalledTimes(1);
});

test('simulator panel keeps GPS exclusion, connection gating and speed command payload', async () => {
  const command = vi.fn().mockResolvedValue(true);
  const simulator: ReturnType<typeof useSimulator> = reactive({ tripId: 1, trip, detail: { trip, route, stops: [start] }, run: null,
    loading: false, busy: false, error: null, select: vi.fn(), retry: vi.fn(), command });
  const wrapper = mount(SimulatorPanel, { props: { simulator, snapshot, connection: 'live', connectionError: null, onReconnect: vi.fn(), onShowRoute: vi.fn(), now: Date.parse(stamp) } }); disposals.push(() => wrapper.unmount());
  await wrapper.get('.play-button').trigger('click'); expect(command).toHaveBeenCalledWith('play');
  await wrapper.setProps({ snapshot: { ...snapshot, positions: [{ id: 1, eventId: 'gps', tripId: 1, vehicleId: 1, latitude: 10.8, longitude: 106.7, source: 'GPS', recordedAt: stamp, receivedAt: stamp, simulatedAt: null, speedKmh: 0, heading: 0, accuracyMeters: 0 }] } });
  expect(wrapper.get('.play-button').attributes('disabled')).toBeDefined();
  simulator.run = { id: 1, tripId: 1, status: 'RUNNING', multiplier: 1, elapsedSeconds: 10, durationSeconds: 100, updatedAt: stamp, simulatedAt: stamp, errorMessage: null, replacementTripId: null, frame: null };
  await wrapper.setProps({ snapshot }); await wrapper.get('button[aria-label="Tốc độ 5x"]').trigger('click'); expect(command).toHaveBeenLastCalledWith('speed', 5);
  await wrapper.setProps({ connection: 'reconnecting' }); expect(wrapper.get('fieldset').attributes('disabled')).toBeDefined();
});

test('sortable stops support keyboard reorder and Escape restores original order', async () => {
  const stops: RouteDraftStop[] = [{ id: 'a', stationId: 1, dwellDurationSeconds: 0 }, { id: 'b', stationId: 2, dwellDurationSeconds: 30 }, { id: 'c', stationId: 3, dwellDurationSeconds: 0 }];
  const change = vi.fn(); const wrapper = mount(SortableStopList, { props: { stops, stations: [], disabled: false, selectedId: null, onChange: change, onFocusStop: vi.fn() } }); disposals.push(() => wrapper.unmount());
  const handle = () => wrapper.get('[data-stop-id="a"] .drag-handle');
  await handle().trigger('keydown', { key: ' ' }); await handle().trigger('keydown', { key: 'ArrowDown' });
  expect(change.mock.calls[0][0].map((stop: RouteDraftStop) => stop.id)).toEqual(['b', 'a', 'c']); await wrapper.setProps({ stops: change.mock.calls[0][0] });
  await handle().trigger('keydown', { key: 'Escape' }); expect(change).toHaveBeenLastCalledWith(stops); expect(wrapper.get('[role=status]').text()).toBe('Đã hủy thay đổi thứ tự.');
});

test('camera accounts for overlays and cancels queued movement/resize callbacks on scope disposal', async () => {
  const root = document.createElement('div'), overlay = document.createElement('aside'); overlay.dataset.mapEdge = 'left'; root.append(overlay);
  root.getBoundingClientRect = () => ({ width: 1000, height: 800, left: 0, top: 0, right: 1000, bottom: 800, x: 0, y: 0, toJSON: () => ({}) });
  overlay.getBoundingClientRect = () => ({ width: 300, height: 600, left: 0, top: 0, right: 300, bottom: 600, x: 0, y: 0, toJSON: () => ({}) });
  overlay.checkVisibility = () => true;
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: false })));
  const disconnect = vi.fn(); vi.stubGlobal('ResizeObserver', class { observe = vi.fn(); disconnect = disconnect; });
  const callbacks = new Map<number, FrameRequestCallback>(); let sequence = 0;
  vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => { callbacks.set(++sequence, callback); return sequence; });
  vi.stubGlobal('cancelAnimationFrame', (id: number) => callbacks.delete(id));
  const scope = effectScope(), map = makeMap(), camera = scope.run(() => useMapCamera(shallowRef(root), shallowRef(map)))!; disposals.push(() => scope.stop());
  const fit = vi.spyOn(map, 'fitBounds').mockReturnValue(map);
  camera.fitBounds(L.latLngBounds([10.8, 106.7], [10.9, 106.8]));
  for (const [id, callback] of [...callbacks]) { callbacks.delete(id); callback(16); }
  expect(fit).toHaveBeenCalledWith(expect.anything(), { paddingTopLeft: [324, 24], paddingBottomRight: [24, 80], maxZoom: 16, animate: false });
  expect(root.style.getPropertyValue('--safe-center-x')).toBe('650px');
  camera.focusLocation([10.8, 106.7]); expect(callbacks.size).toBeGreaterThan(0); scope.stop();
  await nextTick(); await flushPromises(); expect(callbacks.size).toBe(0); expect(disconnect).toHaveBeenCalledTimes(1);
});

test('station panel filters/sorts and edit action does not select its parent card', async () => {
  const station: Station = { id: 1, name: 'Alpha', address: 'Central', latitude: 10.8, longitude: 106.7, checkinRadiusMeters: 50, active: true, createdAt: stamp, updatedAt: stamp };
  const select = vi.fn(), edit = vi.fn();
  const wrapper = mount(StationPanel, { props: { stations: [station, { ...station, id: 2, name: 'Beta', address: 'West', checkinRadiusMeters: 200 }],
    selectedStationId: null, loading: false, error: null, selectionDisabled: false, onBeginCreate: vi.fn(), onSelect: select, onBeginEdit: edit } }); disposals.push(() => wrapper.unmount());
  expect(wrapper.findAll('.station-card-name').map(item => item.text())).toEqual(['Alpha', 'Beta']);
  await wrapper.get('[aria-label="Đổi thứ tự sắp xếp"]').trigger('click'); expect(wrapper.findAll('.station-card-name').map(item => item.text())).toEqual(['Beta', 'Alpha']);
  await wrapper.get('[aria-label="Sửa trạm Alpha"]').trigger('click'); expect(edit).toHaveBeenCalledWith(station); expect(select).not.toHaveBeenCalled();
  await wrapper.get('input').setValue('central'); expect(wrapper.findAll('.station-card')).toHaveLength(1); await wrapper.get('.station-card').trigger('keydown', { key: 'Enter' }); expect(select).toHaveBeenCalledWith(station);
});

test('station form preserves raw fields, radius validation, dirty discard and invalid-details reveal', async () => {
  const form: StationFormState = { name: ' Fixture station ', address: ' ', latitude: '10.8', longitude: '106.7', checkinRadiusMeters: '50' };
  const save = vi.fn().mockResolvedValue(undefined), close = vi.fn(), field = vi.fn();
  const wrapper = mount(StationDrawer, { props: { station: null, mode: 'create', form, saving: false, error: null, pickingLocation: false,
    onClose: close, onBeginEdit: vi.fn(), onPickLocation: vi.fn(), onFieldChange: field, onSave: save, onRequestDeactivate: vi.fn() } }); disposals.push(() => wrapper.unmount());
  await wrapper.get('.radius-number-input').setValue('125'); expect(field).toHaveBeenCalledWith('checkinRadiusMeters', '125');
  await wrapper.setProps({ form: { ...form, checkinRadiusMeters: '1.5' } }); await wrapper.get('form').trigger('submit'); expect(save).not.toHaveBeenCalled();
  expect(wrapper.get('.radius-error-text').text()).toContain('10 đến 1.000');
  await wrapper.get('.radius-number-input').trigger('invalid'); expect((wrapper.get('details').element as HTMLDetailsElement).open).toBe(true);
  await wrapper.setProps({ form }); await wrapper.get('form').trigger('submit'); expect(save).toHaveBeenCalledWith({ name: 'Fixture station', address: null, latitude: 10.8, longitude: 106.7, checkinRadiusMeters: 50 });
  await wrapper.get('[aria-label="Đóng panel"]').trigger('click'); expect(close).not.toHaveBeenCalled(); expect(wrapper.find('.inline-discard-alert').exists()).toBe(true);
  await wrapper.get('.discard-confirm-btn').trigger('click'); expect(close).toHaveBeenCalledTimes(1);
});

test('shape editor requires a fresh preview before save and preserves route-shape payload', async () => {
  const source = { ...route, shapingPoints: [{ destinationStopSequence: 2, latitude: 10.8, longitude: 106.7 }] };
  const saved = vi.fn(); vi.mocked(shapeRoute).mockResolvedValue({ ...route, shapingPoints: [] });
  const wrapper = mount(RouteShapeEditor, { props: { route: source, map: null, onClose: vi.fn(), onSaved: saved } }); disposals.push(() => wrapper.unmount());
  await wrapper.get('[aria-label="Xóa điểm dẫn đường 1"]').trigger('click');
  expect(wrapper.get('.route-drawer-footer .btn-primary').attributes('disabled')).toBeDefined();
  await wrapper.get('.route-drawer-body > .btn-primary').trigger('click'); await flushPromises();
  expect(shapeRoute).toHaveBeenLastCalledWith(1, [], 'preview', expect.any(AbortSignal)); expect(saved).not.toHaveBeenCalled();
  expect(wrapper.get('.route-drawer-footer .btn-primary').attributes('disabled')).toBeUndefined();
  await wrapper.get('.route-drawer-footer .btn-primary').trigger('click'); await flushPromises();
  expect(shapeRoute).toHaveBeenLastCalledWith(1, [], 'save', expect.any(AbortSignal)); expect(saved).toHaveBeenCalledTimes(1);
});

test('shape editor cancels pending preview and removes layers on disposal', async () => {
  const map = makeMap(), source = { ...route, shapingPoints: [{ destinationStopSequence: 2, latitude: 10.8, longitude: 106.7 }] };
  let resolve!: (route: TripDetail['route']) => void;
  vi.mocked(shapeRoute).mockReturnValue(new Promise(result => { resolve = result; }));
  const saved = vi.fn(); const wrapper = mount(RouteShapeEditor, { props: { route: source, map, onClose: vi.fn(), onSaved: saved } });
  expect(layers(map).length).toBeGreaterThan(0);
  await wrapper.get('[aria-label="Xóa điểm dẫn đường 1"]').trigger('click'); await wrapper.get('.route-drawer-body > .btn-primary').trigger('click');
  const signal = vi.mocked(shapeRoute).mock.calls[0][3]!; wrapper.unmount(); expect(signal.aborted).toBe(true); expect(layers(map)).toEqual([]);
  resolve(route); await flushPromises(); expect(saved).not.toHaveBeenCalled(); expect(map.dragging.enabled()).toBe(true);
});
