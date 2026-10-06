import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import L from 'leaflet';
import RouteRevisionComparison from '@/features/reroute/components/RouteRevisionComparison.vue';
import { fetchRouteComparison } from '@/features/reroute/api/comparison';
import { notificationMonitoringLink } from '@/features/reroute/utils/notificationLink';
import type { RouteComparison } from '@/features/reroute/types/comparison';
import { encode, stamp } from './fixtures/driverNavigation';

vi.mock('@/features/reroute/api/comparison', () => ({ fetchRouteComparison: vi.fn() }));
const wrappers: VueWrapper[] = [], maps: L.Map[] = [];
const originalSvg = Object.getOwnPropertyDescriptor(L.Browser, 'svg')!;
function comparison(revisionId = 11): RouteComparison {
  return {
    tripId: 100, revisionId, revisionNumber: revisionId === 11 ? 1 : 2,
    createdAt: stamp, reason: 'Tài xế chọn đường khác để tránh đoạn ùn tắc.',
    status: 'AVAILABLE', message: null, attemptNumber: 1,
    anchor: { latitude: 10.77, longitude: 106.7 },
    before: { encodedPolylines: [encode([[10.77, 106.7], [10.77, 106.701],
      [10.77, 106.703], [10.77, 106.704]])], distanceMeters: 2100, durationSeconds: 2700 },
    after: { encodedPolylines: [encode([[10.77, 106.7], [10.77, 106.701],
      [10.771, 106.701], [10.771, 106.703], [10.77, 106.703], [10.77, 106.704]])],
      distanceMeters: 1400, durationSeconds: 1800 },
  };
}
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(fetchRouteComparison).mockReset().mockResolvedValue(comparison());
  Object.defineProperty(L.Browser, 'svg', { ...originalSvg, value: true });
});
afterEach(() => {
  wrappers.splice(0).forEach(wrapper => wrapper.unmount());
  maps.splice(0).forEach(map => map.remove());
  Object.defineProperty(L.Browser, 'svg', originalSvg);
  document.body.innerHTML = '';
});
function render() {
  const container = document.createElement('div');
  document.body.append(container);
  const map = L.map(container).setView([10.77, 106.7], 15);
  maps.push(map);
  const onFit = vi.fn(), onClose = vi.fn();
  const wrapper = mount(RouteRevisionComparison, { attachTo: document.body,
    props: { map, mapReady: true, tripId: 100, revisionId: 11, visible: true, onFit, onClose } });
  wrappers.push(wrapper);
  return { wrapper, map, container, onFit, onClose };
}
function button(wrapper: VueWrapper, label: string) {
  const match = wrapper.findAll('button').find(candidate => candidate.text() === label);
  expect(match, label).toBeDefined();
  return match!;
}
function paths(container: HTMLElement, kind: 'before' | 'after' | 'common') {
  return container.querySelectorAll(`path.route-comparison-${kind}`);
}
test('renders the saved pair, metrics and all three map modes; does not inject reason HTML', async () => {
  const data = comparison();
  data.reason = '<img src=x onerror=alert(1)>';
  vi.mocked(fetchRouteComparison).mockResolvedValue(data);
  const { wrapper, container, onFit, onClose } = render();
  await flushPromises();
  expect(wrapper.find('img').exists()).toBe(false);
  expect(wrapper.text()).toContain(data.reason);
  expect(wrapper.text()).toContain('2,1 km');
  expect(wrapper.text()).toContain('45 phút');
  expect(wrapper.text()).toContain('1,4 km');
  expect(wrapper.text()).toContain('30 phút');
  expect(wrapper.text()).toContain('không phải vị trí xe hiện tại');
  expect(paths(container, 'before').length).toBeGreaterThan(0);
  expect(paths(container, 'after').length).toBeGreaterThan(0);
  expect(paths(container, 'common').length).toBeGreaterThan(0);
  expect(container.querySelector('path.route-comparison-before')?.getAttribute('stroke-dasharray')).toBe('9 7');
  expect(container.querySelector('path.route-comparison-anchor')).not.toBeNull();
  expect(onFit).toHaveBeenCalledOnce();
  const bounds = onFit.mock.calls[0][0] as L.LatLngBounds;
  expect(bounds.contains([10.771, 106.702])).toBe(true);
  await button(wrapper, 'Trước thay đổi').trigger('click');
  expect(button(wrapper, 'Trước thay đổi').attributes('aria-pressed')).toBe('true');
  expect(paths(container, 'before').length).toBeGreaterThan(0);
  expect(paths(container, 'after')).toHaveLength(0);
  expect(paths(container, 'common')).toHaveLength(0);
  await button(wrapper, 'Sau thay đổi').trigger('click');
  expect(paths(container, 'before')).toHaveLength(0);
  expect(paths(container, 'after').length).toBeGreaterThan(0);
  await button(wrapper, 'So sánh').trigger('click');
  expect(paths(container, 'common').length).toBeGreaterThan(0);
  await button(wrapper, 'Xem vùng thay đổi').trigger('click');
  expect(onFit).toHaveBeenCalledTimes(2);
  await button(wrapper, 'Về giám sát trực tiếp').trigger('click');
  expect(onClose).toHaveBeenCalledOnce();
});

test('unavailable historical before data is explicit and leaves only the saved after path actionable', async () => {
  const data = comparison();
  data.status = 'UNAVAILABLE';
  data.before = null;
  data.message = 'Chưa lưu đủ đường trước thay đổi. Chỉ hiển thị đường sau thay đổi.';
  data.after!.durationSeconds = null;
  vi.mocked(fetchRouteComparison).mockResolvedValue(data);
  const { wrapper, container } = render();
  await flushPromises();
  expect(wrapper.text()).toContain(data.message);
  expect(wrapper.text()).toContain('Chưa có ước tính');
  expect(wrapper.findAll('.route-comparison-metrics > div')).toHaveLength(1);
  expect(button(wrapper, 'So sánh').attributes('disabled')).toBeDefined();
  expect(button(wrapper, 'Trước thay đổi').attributes('disabled')).toBeDefined();
  expect(button(wrapper, 'Sau thay đổi').attributes('disabled')).toBeUndefined();
  expect(button(wrapper, 'Sau thay đổi').attributes('aria-pressed')).toBe('true');
  expect(paths(container, 'before')).toHaveLength(0);
  expect(paths(container, 'common')).toHaveLength(0);
  expect(paths(container, 'after').length).toBeGreaterThan(0);
});

test('error retry fetches again and invalid geometry never leaves a misleading historical overlay', async () => {
  vi.mocked(fetchRouteComparison).mockRejectedValueOnce(new Error('Chưa tải được lịch sử đổi tuyến.'));
  const { wrapper, container } = render();
  await flushPromises();
  expect(wrapper.get('[role="alert"]').text()).toContain('Chưa tải được lịch sử đổi tuyến.');
  expect(container.querySelectorAll('.route-comparison-path')).toHaveLength(0);
  await button(wrapper, 'Thử lại').trigger('click');
  await flushPromises();
  expect(fetchRouteComparison).toHaveBeenCalledTimes(2);
  expect(container.querySelectorAll('.route-comparison-path').length).toBeGreaterThan(0);
  const invalid = comparison(12);
  invalid.after!.encodedPolylines = ['invalid!'];
  vi.mocked(fetchRouteComparison).mockResolvedValueOnce(invalid);
  await wrapper.setProps({ revisionId: 12 });
  await flushPromises();
  expect(wrapper.text()).toContain('Không hiển thị được hình dạng đường đã lưu');
  expect(container.querySelectorAll('.route-comparison-path')).toHaveLength(0);
  expect(button(wrapper, 'Sau thay đổi').attributes('disabled')).toBeDefined();
});

test('switching revisions aborts the old request and ignores a late result; unmount aborts the active request', async () => {
  const pending: { signal: AbortSignal; resolve: (value: RouteComparison) => void }[] = [];
  vi.mocked(fetchRouteComparison).mockImplementation((_trip, _revision, signal) => new Promise(resolve => {
    pending.push({ signal, resolve });
  }));
  const { wrapper } = render();
  expect(wrapper.get('[role="status"]').text()).toContain('Đang tải đường trước và sau');
  expect(pending).toHaveLength(1);
  await wrapper.setProps({ revisionId: 12 });
  expect(pending[0].signal.aborted).toBe(true);
  expect(pending).toHaveLength(2);
  const latest = comparison(12);
  latest.reason = 'Lần đổi thứ hai';
  pending[1].resolve(latest);
  await flushPromises();
  pending[0].resolve(comparison(11));
  await flushPromises();
  expect(wrapper.text()).toContain('Lần đổi #2');
  expect(wrapper.text()).toContain('Lần đổi thứ hai');
  expect(wrapper.text()).not.toContain('Lần đổi #1');
  wrappers.pop(); wrapper.unmount();
  expect(pending[1].signal.aborted).toBe(true);
});

test('hide, revision switch and unmount remove historical layers without disposing the shared map', async () => {
  const { wrapper, map, container } = render();
  await flushPromises();
  const mapRemove = vi.spyOn(map, 'remove');
  const visibleCount = container.querySelectorAll('.route-comparison-path').length;
  expect(visibleCount).toBeGreaterThan(0);
  await wrapper.setProps({ visible: false });
  expect(container.querySelectorAll('.route-comparison-path')).toHaveLength(0);
  expect(container.querySelectorAll('.route-comparison-anchor')).toHaveLength(0);
  await wrapper.setProps({ visible: true });
  expect(container.querySelectorAll('.route-comparison-path')).toHaveLength(visibleCount);
  vi.mocked(fetchRouteComparison).mockResolvedValueOnce(comparison(12));
  await wrapper.setProps({ revisionId: 12 });
  await flushPromises();
  expect(container.querySelectorAll('.route-comparison-path')).toHaveLength(visibleCount);
  wrappers.pop(); wrapper.unmount();
  expect(container.querySelectorAll('.route-comparison-path')).toHaveLength(0);
  expect(container.querySelectorAll('.route-comparison-anchor')).toHaveLength(0);
  expect(container.querySelectorAll('.leaflet-tooltip')).toHaveLength(0);
  expect(mapRemove).not.toHaveBeenCalled();
  expect(map.getCenter().lat).toBeCloseTo(10.77);
});

test('notification links target the exact route-change revision and keep unrelated and legacy links unchanged', () => {
  for (const type of ['REROUTE_CREATED', 'DRIVER_ROUTE_CHANGED'] as const)
    expect(notificationMonitoringLink({ tripId: 100, revisionId: 11, type })).toBe('/operations?tripId=100&revisionId=11');
  for (const revisionId of [null, 0, -1, 1.5, Number.MAX_SAFE_INTEGER + 1])
    expect(notificationMonitoringLink({ tripId: 100, revisionId, type: 'REROUTE_CREATED' })).toBe('/operations?tripId=100');
  expect(notificationMonitoringLink({ tripId: 100, revisionId: 11, type: 'OFF_ROUTE_DETECTED' })).toBe('/operations?tripId=100');
});
