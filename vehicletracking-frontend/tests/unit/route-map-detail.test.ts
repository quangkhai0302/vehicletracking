import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import L from 'leaflet';
import RouteMapDetail from '@/features/routes/components/RouteMapDetail.vue';
import RouteManagementPage from '@/pages/RouteManagementPage.vue';
import { fetchRouteById, fetchRoutes, shapeRoute } from '@/features/routes/api/routes';
import { fetchStations } from '@/features/stations/api/stations';
import { driverSnapshot, encode } from './fixtures/driverNavigation';
import type { RouteDetail, RouteSummary } from '@/features/routes/types/route';

vi.mock('@/features/routes/api/routes', () => ({
  shapeRoute: vi.fn(),
  fetchRoutes: vi.fn(),
  fetchRouteById: vi.fn(),
  createRoute: vi.fn(),
  deactivateRoute: vi.fn(),
}));
vi.mock('@/features/stations/api/stations', () => ({ fetchStations: vi.fn() }));
vi.mock('@/shared/notifications/toast', () => ({ notifySuccess: vi.fn(), notifyError: vi.fn() }));

const route = (): RouteDetail => ({ ...driverSnapshot().route, shapingPoints: [] });
const wrappers: VueWrapper[] = [],
  maps: L.Map[] = [];
const disconnect = vi.fn();
const realMap = L.map;
const originalSvg = Object.getOwnPropertyDescriptor(L.Browser, 'svg')!;
const dialogShow = Object.getOwnPropertyDescriptor(HTMLDialogElement.prototype, 'showModal');
const dialogClose = Object.getOwnPropertyDescriptor(HTMLDialogElement.prototype, 'close');
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(shapeRoute).mockReset();
  vi.mocked(fetchRoutes).mockResolvedValue([
    {
      ...route(),
      startStationName: 'Trạm 1',
      endStationName: 'Trạm 2',
      stopCount: 2,
      active: true,
    } as RouteSummary,
  ]);
  vi.mocked(fetchRouteById).mockResolvedValue(route());
  vi.mocked(fetchStations).mockResolvedValue([]);
  Object.defineProperty(L.Browser, 'svg', { ...originalSvg, value: true });
  vi.spyOn(L, 'map').mockImplementation((...args) => {
    const instance = realMap(...args);
    maps.push(instance);
    return instance;
  });
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      disconnect = disconnect;
    },
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
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount());
  maps.splice(0);
  Object.defineProperty(L.Browser, 'svg', originalSvg);
  for (const [name, descriptor] of [
    ['showModal', dialogShow],
    ['close', dialogClose],
  ] as const) {
    if (descriptor) Object.defineProperty(HTMLDialogElement.prototype, name, descriptor);
    else Reflect.deleteProperty(HTMLDialogElement.prototype, name);
  }
  vi.unstubAllGlobals();
  document.body.innerHTML = '';
});
function detail(data = route()) {
  const callbacks = { onClose: vi.fn(), onRetry: vi.fn(), onSaved: vi.fn(), onCreateTrip: vi.fn() };
  const wrapper = mount(RouteMapDetail, {
    attachTo: document.body,
    props: { route: data, loading: false, error: null, editable: true, ...callbacks },
  });
  wrappers.push(wrapper);
  return { wrapper, ...callbacks };
}
async function click(wrapper: VueWrapper, text: string) {
  const button = wrapper.findAll('button').find((item) => item.text() === text);
  expect(button, `Button ${text}`).toBeDefined();
  await button!.trigger('click');
  await flushPromises();
}
async function edit(wrapper: VueWrapper) {
  await click(wrapper, 'Sửa tuyến đường');
  const paths: L.Polyline[] = [];
  maps[maps.length - 1].eachLayer((layer) => {
    if (layer instanceof L.Polyline && layer.options.color === '#4285f4') paths.push(layer);
  });
  expect(paths).toHaveLength(1);
  paths[0].fire('click', { latlng: L.latLng(10.7704, 106.7006) });
  await flushPromises();
}

test('renders saved geometry and safe numbered stop popups; disposes the map and resize observer', async () => {
  const data = route();
  data.stops[0].stationName = '<img src=x onerror=alert(1)>';
  const { wrapper } = detail(data);
  await flushPromises();
  expect(wrapper.findAll('.leaflet-marker-icon')).toHaveLength(2);
  expect(wrapper.findAll('path[stroke="#0ea5e9"]')).toHaveLength(1);
  const marker = wrapper.get('.leaflet-marker-icon');
  await marker.trigger('click');
  expect(wrapper.get('.leaflet-popup-content strong').text()).toContain(data.stops[0].stationName);
  expect(wrapper.find('.leaflet-popup-content img').exists()).toBe(false);
  await click(wrapper, 'Sửa tuyến đường');
  expect(wrapper.find('.leaflet-popup').exists()).toBe(false);
  const remove = vi.spyOn(maps[0], 'remove');
  wrappers.pop();
  wrapper.unmount();
  expect(remove).toHaveBeenCalledOnce();
  expect(disconnect).toHaveBeenCalledOnce();
});

test('invalid or missing geometry remains explicit and cannot open the editor; detail retry stays available', async () => {
  const data = route();
  data.sections[0].encodedPolyline = 'invalid!';
  const { wrapper, onRetry } = detail(data);
  await flushPromises();
  expect(wrapper.text()).toContain('Chưa hiển thị được đường đi');
  expect(wrapper.find('path[stroke="#0ea5e9"]').exists()).toBe(false);
  const button = wrapper.findAll('button').find((item) => item.text() === 'Sửa tuyến đường')!;
  expect(button.attributes('disabled')).toBeDefined();
  await click(wrapper, 'Tải lại chi tiết');
  expect(onRetry).toHaveBeenCalledOnce();
  await wrapper.setProps({ route: { ...data, sections: [] } });
  expect(button.attributes('disabled')).toBeDefined();
});

test('tile errors provide retry and replace the failed tile layer without hiding saved geometry', async () => {
  const { wrapper } = detail();
  await flushPromises();
  const tiles: L.TileLayer[] = [];
  maps[0].eachLayer((layer) => {
    if (layer instanceof L.TileLayer) tiles.push(layer);
  });
  tiles[0].fire('tileerror');
  await flushPromises();
  expect(wrapper.text()).toContain('Chưa tải được bản đồ nền');
  await click(wrapper, 'Thử lại bản đồ');
  expect(maps[0].hasLayer(tiles[0])).toBe(false);
  expect(wrapper.text()).not.toContain('Chưa tải được bản đồ nền');
  expect(wrapper.find('path[stroke="#0ea5e9"]').exists()).toBe(true);
});

test('loading and detail error avoid creating a map; retry then successful detail creates it', async () => {
  const { wrapper, onRetry } = detail();
  await wrapper.setProps({ route: null, loading: true });
  const created = maps.length;
  expect(wrapper.text()).toContain('Đang tải dữ liệu lộ trình');
  expect(wrapper.find('.leaflet-container').exists()).toBe(false);
  await wrapper.setProps({ loading: false, error: 'Không thể tải chi tiết tuyến.' });
  expect(wrapper.get('[role="alert"]').text()).toContain('Không thể tải chi tiết tuyến.');
  await click(wrapper, 'Tải lại chi tiết');
  expect(onRetry).toHaveBeenCalledOnce();
  expect(maps).toHaveLength(created);
  await wrapper.setProps({ route: route(), error: null });
  await flushPromises();
  expect(wrapper.find('.leaflet-container').exists()).toBe(true);
  expect(maps).toHaveLength(created + 1);
});

test('confirming a dirty parent close invokes the modal close callback', async () => {
  const { wrapper, onClose } = detail();
  await flushPromises();
  await edit(wrapper);
  await wrapper.get('[aria-label="Đóng chi tiết"]').trigger('click');
  expect(onClose).not.toHaveBeenCalled();
  await click(wrapper, 'Bỏ thay đổi');
  expect(onClose).toHaveBeenCalledOnce();
});

test('Escape protects draft; canceling parent close then discarding editor returns to map view', async () => {
  const { wrapper, onClose } = detail();
  await flushPromises();
  await edit(wrapper);
  expect(wrapper.find('path[stroke="#0ea5e9"]').exists()).toBe(false);
  await wrapper.get('.route-map-dialog').trigger('cancel');
  expect(wrapper.find('.fleet-confirm').exists()).toBe(true);
  expect(onClose).not.toHaveBeenCalled();
  await click(wrapper, 'Quay lại');
  expect(wrapper.find('.route-shape-handle').exists()).toBe(true);
  await click(wrapper, 'Hủy');
  await click(wrapper, 'Bỏ thay đổi');
  expect(onClose).not.toHaveBeenCalled();
  expect(wrapper.find('.route-drawer').exists()).toBe(false);
  expect(wrapper.find('path[stroke="#0ea5e9"]').exists()).toBe(true);
  await wrapper.get('[aria-label="Đóng chi tiết"]').trigger('click');
  expect(onClose).toHaveBeenCalledOnce();
});

test('preview blocks close and duplicate requests; save failure retains the draft for copying', async () => {
  const { wrapper, onClose, onSaved } = detail();
  await flushPromises();
  await edit(wrapper);
  let finish!: (data: RouteDetail) => void;
  vi.mocked(shapeRoute).mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  );
  const previewButton = wrapper.findAll('button').find((item) => item.text() === 'Tính lại tuyến')!;
  await previewButton.trigger('click');
  await previewButton.trigger('click');
  await wrapper.get('.route-map-dialog').trigger('cancel');
  expect(onClose).not.toHaveBeenCalled();
  expect(shapeRoute).toHaveBeenCalledOnce();
  expect(wrapper.get('[aria-label="Đóng chi tiết"]').attributes('disabled')).toBeDefined();
  finish(route());
  await flushPromises();
  vi.mocked(shapeRoute).mockRejectedValueOnce(
    new Error('Tuyến đã có chuyến; hãy lưu thành tuyến mới.'),
  );
  await click(wrapper, 'Lưu tuyến');
  expect(onSaved).not.toHaveBeenCalled();
  expect(wrapper.find('.route-shape-handle').exists()).toBe(true);
  await wrapper.get('input[type="checkbox"]').setValue(true);
  const copy = { ...route(), id: 2, name: 'Tuyến mới' };
  vi.mocked(shapeRoute).mockResolvedValueOnce(copy);
  await click(wrapper, 'Lưu tuyến');
  expect(shapeRoute).toHaveBeenLastCalledWith(
    1,
    [{ destinationStopSequence: 2, latitude: 10.7704, longitude: 106.7006 }],
    'copy',
    expect.any(AbortSignal),
  );
  expect(onSaved).toHaveBeenCalledWith(copy);
});

test('copy updates detail, list and deep link; create trip selects the returned route', async () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/routes', component: RouteManagementPage },
      { path: '/trips', component: { template: '<p>Trips</p>' } },
    ],
  });
  await router.push('/routes?routeId=1');
  await router.isReady();
  const wrapper = mount(RouteManagementPage, {
    attachTo: document.body,
    global: { plugins: [router] },
  });
  wrappers.push(wrapper);
  await flushPromises();
  await edit(wrapper);
  vi.mocked(shapeRoute).mockResolvedValueOnce(route());
  await click(wrapper, 'Tính lại tuyến');
  await wrapper.get('.route-shape-copy input').setValue(true);
  const copy = {
    ...route(),
    id: 2,
    name: 'Tuyến mới từ bản đồ',
    totalDistanceMeters: 250,
    sections: [
      {
        ...route().sections[0],
        encodedPolyline: encode([
          [10.77, 106.7],
          [10.7705, 106.702],
          [10.771, 106.701],
        ]),
      },
    ],
  };
  vi.mocked(shapeRoute).mockResolvedValueOnce(copy);
  await click(wrapper, 'Lưu tuyến');
  expect(router.currentRoute.value.query.routeId).toBe('2');
  expect(wrapper.get('.route-map-dialog h2').text()).toBe(copy.name);
  expect(wrapper.findAll('.route-management-section tbody tr')).toHaveLength(2);
  expect(wrapper.get('.route-map-summary').text()).toContain('0.3 km');
  const lines: L.Polyline[] = [];
  maps[maps.length - 1].eachLayer((layer) => {
    if (layer instanceof L.Polyline && layer.options.color === '#0ea5e9') lines.push(layer);
  });
  expect(lines[0].getLatLngs()).toEqual([
    L.latLng(10.77, 106.7),
    L.latLng(10.7705, 106.702),
    L.latLng(10.771, 106.701),
  ]);
  expect(fetchRouteById).toHaveBeenCalledOnce();
  await click(wrapper, 'Tạo chuyến từ tuyến này');
  expect(router.currentRoute.value.path).toBe('/trips');
  expect(router.currentRoute.value.query).toEqual({ routeId: '2', create: '1' });
});

test('unmount aborts an in-flight editor preview and ignores its late response', async () => {
  const { wrapper, onSaved } = detail();
  await flushPromises();
  await edit(wrapper);
  let finish!: (data: RouteDetail) => void;
  vi.mocked(shapeRoute).mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  );
  await click(wrapper, 'Tính lại tuyến');
  const signal = vi.mocked(shapeRoute).mock.calls[0][3]!;
  wrappers.pop();
  wrapper.unmount();
  expect(signal.aborted).toBe(true);
  finish(route());
  await flushPromises();
  expect(onSaved).not.toHaveBeenCalled();
});
