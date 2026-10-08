import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import { fetchHereIncidents, fetchHereTrafficFlow } from '@/features/traffic/api/hereTraffic';
vi.mock('@/features/traffic/api/hereTraffic', () => ({ fetchHereIncidents: vi.fn(), fetchHereTrafficFlow: vi.fn() }));
import L from 'leaflet';
import DriverNavigationMap from '@/features/fleet/components/DriverNavigationMap.vue';
import { driverSnapshot } from './fixtures/driverNavigation';
import type { StopVisit } from '@/features/fleet/types/checkin';
const svg = Object.getOwnPropertyDescriptor(L.Browser, 'svg')!;
const disconnect = vi.fn(), observe = vi.fn();
const cleanups: (() => void)[] = [];
beforeEach(() => {
  Object.defineProperty(L.Browser, 'svg', { ...svg, value: true });
  disconnect.mockClear(); observe.mockClear();
  const envelope = { source: 'HERE_LIVE' as const, status: 'AVAILABLE' as const, observedAt: null, fetchedAt: '2026-10-08T00:00:00Z', ageSeconds: 0, warning: null, results: [] };
  vi.mocked(fetchHereIncidents).mockReset().mockResolvedValue(envelope);
  vi.mocked(fetchHereTrafficFlow).mockReset().mockResolvedValue(envelope);
  vi.stubGlobal('ResizeObserver', class { observe = observe; disconnect = disconnect; });
});
afterEach(() => { cleanups.splice(0).forEach(fn => fn()); Object.defineProperty(L.Browser, 'svg', svg); vi.unstubAllGlobals(); vi.restoreAllMocks(); document.body.replaceChildren(); });
function setup(data = driverSnapshot('IN_PROGRESS')) {
  const wrapper = mount(DriverNavigationMap, { attachTo: document.body,
    props: { snapshot: data, now: Date.parse(data.serverTime), connected: true, preview: null } });
  cleanups.push(() => wrapper.unmount()); return { wrapper, data };
}
const visit = (attemptNumber = 1): StopVisit => ({ id: 1, tripId: 7, stopSequence: 1, source: 'SIMULATOR', evidenceKind: 'POINT',
  actualArrivalAt: '2026-09-29T02:00:00Z', simulatedArrivalAt: null, detectedAt: '2026-09-29T02:00:00Z',
  fromSampleId: null, toSampleId: 1, evidenceFraction: 0, latitude: 10.77, longitude: 106.7, attemptNumber });
test('uses admin station icons, permanent names and typed vehicle glyph', async () => {
  const { wrapper } = setup();
  await nextTick();
  expect(wrapper.findAll('.route-stop-map-marker')).toHaveLength(2);
  expect(wrapper.find('.route-stop-map-marker.start .route-stop-map-marker-sequence').text()).toBe('1');
  expect(wrapper.find('.route-stop-map-marker.end .route-stop-map-marker-sequence').text()).toBe('2');
  expect(wrapper.findAll('.operational-stop-label').map(x => x.text())).toEqual(['#1 · Trạm 1', '#2 · Trạm 2']);
  expect(wrapper.findAll('.live-vehicle-glyph')).toHaveLength(1);
  expect(wrapper.find('.live-vehicle-marker').attributes('data-vehicle-type')).toBe('CAR');
  expect(wrapper.find('path[stroke="#4285f4"]').exists()).toBe(true);
  expect(observe).toHaveBeenCalledTimes(1);
});
test('preview does not replace official geometry or stop markers', async () => {
  const { wrapper, data } = setup();
  await wrapper.setProps({ preview: data.route.sections });
  expect(wrapper.find('path[stroke="#f59e0b"]').exists()).toBe(true);
  expect(wrapper.find('path[stroke="#4285f4"]').exists()).toBe(true);
  expect(wrapper.findAll('.route-stop-map-marker')).toHaveLength(2);
  expect(wrapper.findAll('.live-vehicle-glyph')).toHaveLength(1);
  expect(wrapper.find('.driver-map-center').classes()).not.toContain('active');
  await wrapper.setProps({ preview: null }); expect(wrapper.find('path[stroke="#f59e0b"]').exists()).toBe(false);
});
test('popup safely displays station address, radius and dwell like admin', async () => {
  const data = driverSnapshot('IN_PROGRESS');
  data.stops[1]!.stationName = '<img src=x onerror=alert(1)>';
  data.stations[1]!.address = '<script>alert(1)</script>';
  const { wrapper } = setup(data);
  wrapper.vm.showStop(2);
  expect(wrapper.find('.simulation-stop-popup').text()).toContain(data.stops[1]!.stationName);
  expect(wrapper.find('.simulation-stop-popup').text()).toContain(data.stations[1]!.address);
  expect(wrapper.find('.simulation-stop-popup').text()).toContain('50 m');
  expect(wrapper.find('.simulation-stop-popup').text()).toContain('Trạm kế tiếp');
  expect(wrapper.find('.simulation-stop-popup img').exists()).toBe(false);
  expect(wrapper.find('.simulation-stop-popup script').exists()).toBe(false);
});
test('check-in updates preserve the open popup and marker instances without using old attempts', async () => {
  const { wrapper, data } = setup();
  const first = wrapper.find('.route-stop-map-marker.start').element;
  expect(first.classList.contains('simulation-checked-in')).toBe(false);
  wrapper.vm.showStop(1);
  const popup = wrapper.find('.simulation-stop-popup').element;
  await wrapper.setProps({ snapshot: { ...data, checkIns: { ...data.checkIns, revision: 1, visits: [visit(0)] } } });
  expect(wrapper.find('.route-stop-map-marker.start').classes()).not.toContain('simulation-checked-in');
  await wrapper.setProps({ snapshot: { ...data, checkIns: { ...data.checkIns, revision: 2, visits: [visit()] } } });
  expect(wrapper.find('.route-stop-map-marker.start').classes()).toContain('simulation-checked-in');
  expect(wrapper.find('.simulation-stop-popup').element).toBe(popup);
  expect(wrapper.find('.simulation-stop-popup').text()).toContain('Đã check-in');
  expect(wrapper.findAll('.route-stop-map-marker')).toHaveLength(2);
});
test('overview and follow controls switch camera without changing route state', async () => {
  const fit = vi.spyOn(L.Map.prototype, 'fitBounds'), pan = vi.spyOn(L.Map.prototype, 'panTo');
  const { wrapper, data } = setup();
  await wrapper.find('.driver-map-center').trigger('click');
  expect(wrapper.find('.driver-map-center').attributes('aria-pressed')).toBe('true');
  expect(pan).toHaveBeenCalled();
  await wrapper.find('.driver-map-overview').trigger('click');
  expect(fit).toHaveBeenCalledTimes(2);
  expect(wrapper.find('.driver-map-center').attributes('aria-pressed')).toBe('false');
  expect(data.routeRevisionId).toBeNull();
});
test('planned motorcycle and paused/offline vehicle use admin presentation', async () => {
  const data = driverSnapshot(); data.trip.vehicleType = 'MOTORCYCLE';
  const { wrapper } = setup(data);
  await nextTick();
  expect(wrapper.find('.live-vehicle-marker').attributes('data-vehicle-type')).toBe('MOTORCYCLE');
  expect(wrapper.find('.live-vehicle-marker').classes()).toContain('planned');
  const running = driverSnapshot('IN_PROGRESS'); running.trip.vehicleType = 'MOTORCYCLE'; running.simulation!.status = 'PAUSED';
  running.position!.heading = 120;
  await wrapper.setProps({ snapshot: running });
  expect(wrapper.findAll('.live-vehicle-glyph')).toHaveLength(1);
  expect(wrapper.find('.live-vehicle-marker').attributes('style')).toContain('120deg');
  expect(wrapper.find('.live-vehicle-marker').classes()).toContain('muted');
  await wrapper.setProps({ connected: false });
  expect(wrapper.find('.live-vehicle-marker').classes()).toContain('muted');
});
test('malformed geometry shows an error but does not hide stations', async () => {
  const { wrapper, data } = setup();
  await wrapper.setProps({ snapshot: { ...data, route: { ...data.route, sections: [{ ...data.route.sections[0]!, encodedPolyline: 'invalid' }] } } });
  expect(wrapper.find('[role="alert"]').text()).toContain('Không thể hiển thị hình học');
  expect(wrapper.findAll('.route-stop-map-marker')).toHaveLength(2);
});
test('unmount removes Leaflet map, observer and resize listener', () => {
  const remove = vi.spyOn(L.Map.prototype, 'remove'), cleanup = vi.spyOn(window, 'removeEventListener');
  const { wrapper } = setup(); wrapper.unmount(); cleanups.pop();
  expect(remove).toHaveBeenCalledTimes(1); expect(disconnect).toHaveBeenCalledTimes(1);
  expect(cleanup).toHaveBeenCalledWith('resize', expect.any(Function));
});

test('traffic switch changes road tiles and incident visibility without changing the trip', async () => {
  vi.mocked(fetchHereIncidents).mockResolvedValue({ source: 'HERE_LIVE', status: 'AVAILABLE', observedAt: null,
    fetchedAt: '2026-10-08T00:00:00Z', ageSeconds: 0, warning: null,
    results: [{ id: 'fixture', type: 'closure', description: 'Đường đang đóng', criticality: 'major', points: [], center: [10.77, 106.7] }] });
  const setUrl = vi.spyOn(L.TileLayer.prototype, 'setUrl');
  const { wrapper, data } = setup();
  await nextTick(); await nextTick(); await nextTick();
  const toggle = wrapper.get('button[aria-label="Giao thông theo thời gian thực"]');
  expect(toggle.attributes('aria-pressed')).toBe('true');
  expect(fetchHereIncidents).toHaveBeenCalledTimes(1);
  expect(wrapper.find('.gm-traffic-incident-badge').exists()).toBe(true);
  await toggle.trigger('click');
  expect(toggle.attributes('aria-pressed')).toBe('false');
  expect(setUrl).toHaveBeenLastCalledWith(expect.stringContaining('lyrs=m&'));
  expect(wrapper.find('.gm-traffic-incident-badge').exists()).toBe(false);
  await toggle.trigger('click');
  await nextTick(); await nextTick();
  expect(setUrl).toHaveBeenLastCalledWith(expect.stringContaining('lyrs=m,traffic&'));
  expect(fetchHereIncidents).toHaveBeenCalledTimes(2);
  expect(wrapper.findAll('.route-stop-map-marker')).toHaveLength(2);
  expect(data.trip.status).toBe('IN_PROGRESS');
});
