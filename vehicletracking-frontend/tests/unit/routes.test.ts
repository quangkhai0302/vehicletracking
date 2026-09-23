import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import RouteWorkspace from '@/features/routes/components/RouteWorkspace.vue';
import RouteCreateContent from '@/features/routes/components/RouteCreateContent.vue';
import * as api from '@/features/routes/api/routes';
import type { RouteDetail, RouteSummary } from '@/features/routes/types/route';
import type { Station } from '@/features/stations/types/station';
vi.mock('@/features/routes/api/routes', () => ({ fetchRoutes: vi.fn(), fetchRouteById: vi.fn(), createRoute: vi.fn(), updateRoute: vi.fn(), deactivateRoute: vi.fn(), shapeRoute: vi.fn() }));
const stamp = '2026-09-23T01:00:00Z';
const stations: Station[] = [1, 2, 3].map(id => ({ id, name: `Station ${id}`, address: null, latitude: 10.8, longitude: 106.7, checkinRadiusMeters: 100, active: true, createdAt: stamp, updatedAt: stamp }));
const route = (id = 1): RouteDetail => ({ id, name: `Route ${id}`, transportMode: 'CAR', routingProvider: 'HERE', totalDistanceMeters: 1000, estimatedTravelDurationSeconds: 600,
  baseTravelDurationSeconds: 600, totalDwellDurationSeconds: 0, estimatedTripDurationSeconds: 600, estimatedDepartureAt: stamp, calculatedAt: stamp, createdAt: stamp, sections: [],
  stops: stations.slice(0, 2).map((station, i) => ({ sequenceNumber: i + 1, role: i === 0 ? 'START' : 'END', stationId: station.id, stationName: station.name, latitude: station.latitude,
    longitude: station.longitude, dwellDurationSeconds: 0, distanceFromPreviousMeters: i * 1000, travelDurationFromPreviousSeconds: i * 600, arrivalOffsetSeconds: i * 600, departureOffsetSeconds: i * 600 })) });
const summary = (id = 1): RouteSummary => ({ ...route(id), startStationName: 'Station 1', endStationName: 'Station 2', stopCount: 2 });
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(r => { resolve = r; }); return { promise, resolve }; }
const unmounts: (() => void)[] = [];
beforeEach(() => { vi.resetAllMocks(); vi.mocked(api.fetchRoutes).mockResolvedValue([summary(1), summary(2)]); vi.mocked(api.fetchRouteById).mockImplementation(id => Promise.resolve(route(id))); });
afterEach(() => { unmounts.splice(0).forEach(unmount => unmount()); });
function workspace() {
  const display = vi.fn(), toast = vi.fn();
  const wrapper = mount(RouteWorkspace, { props: { map: null, stations, loadingStations: false, onDraftStopsChange: vi.fn(), selectedDraftStopId: null,
    onFocusDraftStop: vi.fn(), onFocusStop: vi.fn(), onPlannedRouteDisplay: display, onShowToast: toast } });
  unmounts.push(() => wrapper.unmount()); return { wrapper, display, toast };
}
test('route selection clears prior map geometry; late A cannot replace B and disposal clears display', async () => {
  const a = deferred<RouteDetail>(), b = deferred<RouteDetail>(); vi.mocked(api.fetchRouteById).mockImplementation(id => id === 1 ? a.promise : b.promise);
  const { wrapper, display } = workspace(); await flushPromises();
  await wrapper.findAll('.route-card')[0].trigger('click'); await wrapper.findAll('.route-card')[1].trigger('click');
  expect(display).toHaveBeenLastCalledWith(null); expect(vi.mocked(api.fetchRouteById).mock.calls[0][1]?.aborted).toBe(true);
  b.resolve(route(2)); await flushPromises(); a.resolve(route(1)); await flushPromises(); expect(wrapper.get('.route-drawer h3').text()).toBe('Route 2'); expect(display).toHaveBeenLastCalledWith(route(2));
  wrapper.unmount(); expect(display).toHaveBeenLastCalledWith(null); expect(vi.mocked(api.fetchRouteById).mock.calls[1][1]?.aborted).toBe(true);
});
test('station refresh does not overwrite an editable draft; closing does not duplicate detail fetch', async () => {
  const { wrapper } = workspace(); await flushPromises(); await wrapper.get('.route-card').trigger('click'); await flushPromises();
  await wrapper.findAll('.route-drawer-footer button').find(button => button.text() === 'Sửa tuyến')!.trigger('click');
  await wrapper.get('.form-input').setValue('Unsaved route'); await wrapper.setProps({ refreshToken: 1 }); await flushPromises();
  expect(api.fetchRouteById).toHaveBeenCalledTimes(1); expect((wrapper.get('.form-input').element as HTMLInputElement).value).toBe('Unsaved route');
  await wrapper.get('.drawer-close-btn').trigger('click'); await wrapper.get('.discard-confirm .danger-action').trigger('click'); await flushPromises();
  await wrapper.get('.route-card').trigger('click'); await flushPromises(); expect(api.fetchRouteById).toHaveBeenCalledTimes(2);
});
test('pending route creation locks selection and ignores response after unmount', async () => {
  const pending = deferred<RouteDetail>(); vi.mocked(api.createRoute).mockReturnValue(pending.promise);
  const { wrapper, toast } = workspace(); await flushPromises(); await wrapper.get('.btn-primary-create').trigger('click');
  await wrapper.get('.form-input').setValue(' New route '); await wrapper.get('form').trigger('submit');
  expect(api.createRoute).toHaveBeenCalledWith({ name: 'New route', stops: [{ stationId: 1, dwellDurationSeconds: 0 }, { stationId: 2, dwellDurationSeconds: 0 }] });
  await wrapper.get('.route-card').trigger('click'); expect(api.fetchRouteById).not.toHaveBeenCalled();
  wrapper.unmount(); pending.resolve(route(3)); await flushPromises(); expect(toast).not.toHaveBeenCalled();
});
test('route editor preserves repeated nonadjacent stations but blocks adjacent duplicates', async () => {
  const save = vi.fn(), draft = vi.fn();
  const wrapper = mount(RouteCreateContent, { props: { stations, saving: false, error: null, onClose: vi.fn(), onSaveRoute: save, onDraftStopsChange: draft, selectedDraftStopId: null, onFocusDraftStop: vi.fn() } }); unmounts.push(() => wrapper.unmount());
  await wrapper.get('.form-input').setValue('Loop'); await wrapper.get('.btn-add-stop').trigger('click');
  expect(draft.mock.lastCall?.[0].map((stop: { stationId: number }) => stop.stationId)).toEqual([1, 2, 1]);
  await wrapper.get('form').trigger('submit'); expect(save).toHaveBeenCalledTimes(1);
  await wrapper.findAll('select')[1].setValue('1'); await wrapper.get('form').trigger('submit'); expect(save).toHaveBeenCalledTimes(1);
  expect(wrapper.text()).toContain('Hai điểm liền nhau phải là hai trạm khác nhau.'); wrapper.unmount(); expect(draft).toHaveBeenLastCalledWith([]);
});
test('route draft refuses inactive stations and normalizes dwell when reordering endpoints', async () => {
  const save = vi.fn(), draft = vi.fn();
  const wrapper = mount(RouteCreateContent, { props: { stations, saving: false, error: null, onClose: vi.fn(), onSaveRoute: save, onDraftStopsChange: draft, selectedDraftStopId: null, onFocusDraftStop: vi.fn() } }); unmounts.push(() => wrapper.unmount());
  await wrapper.get('.form-input').setValue('Fixture route'); await wrapper.get('.btn-add-stop').trigger('click');
  await wrapper.get('input[type=number]').setValue('120'); await wrapper.get('[aria-label="Di chuyển điểm 2 lên"]').trigger('click');
  expect(draft.mock.lastCall?.[0][0].dwellDurationSeconds).toBe(0);
  await wrapper.setProps({ stations: stations.map(station => ({ ...station, active: station.id !== 1 })) });
  await wrapper.get('form').trigger('submit'); expect(save).not.toHaveBeenCalled(); expect(wrapper.text()).toContain('Có trạm đã ngừng hoạt động.');
});
