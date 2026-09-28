import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { effectScope, nextTick, shallowRef } from 'vue';
import { flushPromises, mount } from '@vue/test-utils';
import * as api from '@/features/fleet/api/fleet';
import { fetchTripEta } from '@/features/fleet/api/eta';
import { fetchTripCheckIns } from '@/features/fleet/api/checkins';
import { fetchRoutes } from '@/features/routes/api/routes';
import { useFleetWorkspace } from '@/features/fleet/composables/useFleetWorkspace';
import { useTripEta } from '@/features/fleet/composables/useTripEta';
import { useTripCheckIns } from '@/features/fleet/composables/useTripCheckIns';
import VehicleEditor from '@/features/fleet/components/VehicleEditor.vue';
import DriverEditor from '@/features/fleet/components/DriverEditor.vue';
import TripEditor from '@/features/fleet/components/TripEditor.vue';
import TripDetailPanel from '@/features/fleet/components/TripDetailPanel.vue';
import FleetWorkspace from '@/features/fleet/components/FleetWorkspace.vue';
import type { Driver, FleetVehicle, TripDetail, TripSummary } from '@/features/fleet/types/fleet';
import type { OperationsSnapshot } from '@/features/tracking/types/operations';
import type { TripEta } from '@/features/fleet/types/eta';
import { displayTripTime } from '@/features/fleet/utils/tripTime';

vi.mock('@/features/fleet/api/fleet', () => ({
  fetchFleetVehicles: vi.fn(),
  fetchDrivers: vi.fn(),
  fetchTrips: vi.fn(),
  fetchTrip: vi.fn(),
  createVehicle: vi.fn(),
  updateVehicle: vi.fn(),
  assignVehicleDriver: vi.fn(),
  unassignVehicleDriver: vi.fn(),
  deactivateVehicle: vi.fn(),
  createDriver: vi.fn(),
  updateDriver: vi.fn(),
  deactivateDriver: vi.fn(),
  createTrip: vi.fn(),
  changeTripStatus: vi.fn(),
  deleteTrip: vi.fn(),
  assignTripDriver: vi.fn(),
  unassignTripDriver: vi.fn(),
  assignTripVehicle: vi.fn(),
}));
vi.mock('@/features/fleet/api/eta', () => ({ fetchTripEta: vi.fn() }));
vi.mock('@/features/fleet/api/checkins', () => ({ fetchTripCheckIns: vi.fn() }));
vi.mock('@/features/routes/api/routes', () => ({ fetchRoutes: vi.fn() }));
const stamp = '2026-09-22T01:00:00Z';
const driver: Driver = {
  id: 1,
  fullName: 'Fixture Driver',
  phoneNumber: '0901234567',
  licenseNumber: 'B2-123',
  active: true,
  createdAt: stamp,
  updatedAt: stamp,
};
const vehicle: FleetVehicle = {
  id: 1,
  name: 'Fixture Car',
  plateNumber: '51B12345',
  description: null,
  vehicleType: 'CAR',
  active: true,
  driver,
  createdAt: stamp,
  updatedAt: stamp,
};
const trip = (
  id: number,
  attemptNumber = 1,
  status: TripSummary['status'] = 'SCHEDULED',
): TripSummary => ({
  id,
  attemptNumber,
  status,
  vehicleId: 1,
  vehiclePlateNumber: vehicle.plateNumber,
  vehicleType: 'CAR',
  routeId: 1,
  routeName: 'Fixture route',
  scheduledDepartureAt: stamp,
  plannedEndAt: stamp,
  startedAt: null,
  endedAt: null,
  createdAt: stamp,
  dispatchMode: 'ON_DEMAND',
  scheduleId: null,
  scheduleName: null,
  driver,
});
const route: TripDetail['route'] = {
  id: 1,
  name: 'Fixture route',
  transportMode: 'CAR',
  routingProvider: 'HERE',
  totalDistanceMeters: 1000,
  estimatedTravelDurationSeconds: 600,
  baseTravelDurationSeconds: 600,
  totalDwellDurationSeconds: 60,
  estimatedTripDurationSeconds: 660,
  estimatedDepartureAt: stamp,
  calculatedAt: stamp,
  createdAt: stamp,
  stops: [],
  sections: [],
};
const detail = (id: number, attempt = 1): TripDetail => ({
  trip: trip(id, attempt),
  stops: [],
  route,
});
const snapshot = (trips: TripSummary[] = []): OperationsSnapshot => ({
  serverTime: stamp,
  trips,
  positions: [],
  simulations: [],
  checkIns: [],
  notifications: [],
});
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((r) => {
    resolve = r;
  });
  return { promise, resolve };
}
const scopes: ReturnType<typeof effectScope>[] = [];
function scoped<T>(factory: () => T): T {
  const scope = effectScope();
  scopes.push(scope);
  return scope.run(factory)!;
}
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.fetchFleetVehicles).mockResolvedValue([vehicle]);
  vi.mocked(api.fetchDrivers).mockResolvedValue([driver]);
  vi.mocked(api.fetchTrips).mockResolvedValue([trip(1), trip(2)]);
  vi.mocked(api.fetchTrip).mockImplementation((id) => Promise.resolve(detail(id)));
  vi.mocked(fetchTripCheckIns).mockResolvedValue({
    tripId: 1,
    revision: 0,
    nextStopSequence: null,
    awaitingExit: false,
    visits: [],
  });
  vi.mocked(fetchTripEta).mockResolvedValue({
    tripId: 1,
    routeId: 1,
    calculatedAt: stamp,
    source: 'ROUTE_SNAPSHOT',
    status: 'AVAILABLE',
    trafficObservedAt: null,
    trafficFetchedAt: null,
    nextStopSequence: null,
    baselineRemainingSeconds: 0,
    totalRemainingSeconds: 0,
    stops: [],
    affectedSegments: [],
    warning: null,
  });
});
afterEach(() => {
  scopes.splice(0).forEach((scope) => scope.stop());
  vi.useRealTimers();
});

test('trip detail replaces the list as a full-page workspace and returns without a modal', async () => {
  const wrapper = mount(FleetWorkspace, {
    props: {
      initialTab: 'trips',
      lockedTab: 'trips',
      onToast: vi.fn(),
      onFocusStop: vi.fn(),
      onManageRoutes: vi.fn(),
      onManageStations: vi.fn(),
    },
    global: {
      stubs: {
        TripDetailPanel: {
          props: ['onClose'],
          template:
            '<section class="trip-detail-panel-stub"><button aria-label="Đóng chi tiết chuyến" @click="onClose()">Quay lại</button></section>',
        },
      },
    },
  });
  await flushPromises();

  expect(wrapper.findAll('.fleet-summary-card')).toHaveLength(4);
  expect(wrapper.text()).toContain('Tổng chuyến đi');
  expect(wrapper.text()).toContain('Chờ khởi hành');
  expect(wrapper.text()).toContain('Đã hoàn thành');

  expect(wrapper.get('.fleet-list-view').attributes('hidden')).toBeUndefined();
  await wrapper.get('[aria-label="Mở chi tiết chuyến 1, Fixture route"]').trigger('click');
  await flushPromises();

  expect(wrapper.get('.fleet-list-view').attributes('hidden')).toBe('');
  expect(wrapper.find('.trip-detail-panel-stub').exists()).toBe(true);
  expect(wrapper.find('.business-side-panel').exists()).toBe(false);

  await wrapper.get('[aria-label="Đóng chi tiết chuyến"]').trigger('click');
  await nextTick();

  expect(wrapper.get('.fleet-list-view').attributes('hidden')).toBeUndefined();
  expect(wrapper.find('.trip-detail-panel-stub').exists()).toBe(false);
  wrapper.unmount();
});

test('detail A cannot overwrite B; disposal aborts outstanding reads', async () => {
  const a = deferred<TripDetail>(),
    b = deferred<TripDetail>();
  vi.mocked(api.fetchTrip).mockImplementation((id) => (id === 1 ? a.promise : b.promise));
  const fleet = scoped(() => useFleetWorkspace(vi.fn()));
  await flushPromises();
  const first = fleet.selectTrip(1);
  const signalA = vi.mocked(api.fetchTrip).mock.calls[0][1]!;
  const second = fleet.selectTrip(2);
  expect(signalA.aborted).toBe(true);
  b.resolve(detail(2));
  await second;
  a.resolve(detail(1));
  await first;
  expect(fleet.detail?.trip.id).toBe(2);
  const signalB = vi.mocked(api.fetchTrip).mock.calls[1][1]!;
  scopes[0].stop();
  expect(signalB.aborted).toBe(true);
});

test('only the latest selection is applied after an in-flight mutation', async () => {
  const pending = deferred<Driver>();
  vi.mocked(api.createDriver).mockReturnValue(pending.promise);
  const selection = shallowRef<{ tripId: number | null } | null>(null);
  const fleet = scoped(() => useFleetWorkspace(vi.fn(), undefined, selection));
  await flushPromises();
  const saving = fleet.saveDriver(driver);
  expect(fleet.busy).toBe(true);
  selection.value = { tripId: 1 };
  await nextTick();
  selection.value = { tripId: 2 };
  await nextTick();
  expect(api.fetchTrip).not.toHaveBeenCalled();
  pending.resolve(driver);
  await saving;
  await flushPromises();
  expect(api.fetchTrip).toHaveBeenCalledTimes(1);
  expect(fleet.detail?.trip.id).toBe(2);
  fleet.close();
  await nextTick();
  expect(fleet.screen.kind).toBe('list');
});

test('new replay hides old detail and reloads exactly once', async () => {
  const live = shallowRef(snapshot([trip(1)]));
  const pending = deferred<TripDetail>();
  const fleet = scoped(() => useFleetWorkspace(vi.fn(), live));
  await flushPromises();
  await fleet.selectTrip(1);
  vi.mocked(api.fetchTrip).mockReturnValueOnce(pending.promise);
  live.value = snapshot([trip(1, 2)]);
  await nextTick();
  expect(fleet.detail).toBeNull();
  pending.resolve(detail(1, 2));
  await flushPromises();
  expect(fleet.detail?.trip.attemptNumber).toBe(2);
  expect(api.fetchTrip).toHaveBeenCalledTimes(2);
});

test('locally completed trip is not reverted by older realtime status', async () => {
  const live = shallowRef(snapshot([trip(1, 1, 'IN_PROGRESS')]));
  const fleet = scoped(() => useFleetWorkspace(vi.fn(), live));
  await flushPromises();
  await fleet.selectTrip(1);
  vi.mocked(api.changeTripStatus).mockResolvedValue({
    ...detail(1),
    trip: trip(1, 1, 'COMPLETED'),
  });
  await fleet.transition('complete');
  live.value = snapshot([trip(1, 1, 'SCHEDULED')]);
  await nextTick();
  expect(fleet.detail?.trip.status).toBe('COMPLETED');
  expect(fleet.trips.find((t) => t.id === 1)?.status).toBe('COMPLETED');
});

test('new vehicle never triggers a follow-up driver assignment', async () => {
  const fleet = scoped(() => useFleetWorkspace(vi.fn()));
  await flushPromises();
  vi.mocked(api.createVehicle).mockResolvedValue({ ...vehicle, driver: null });
  expect(
    await fleet.saveVehicle({
      plateNumber: vehicle.plateNumber,
      name: vehicle.name,
      description: null,
      vehicleType: 'CAR',
      driverId: 1,
    }),
  ).toBe(true);
  expect(api.assignVehicleDriver).not.toHaveBeenCalled();
  expect(fleet.vehicles[0].driver).toBeNull();
});

test('vehicle assignment partial failure refreshes committed vehicle state when editing', async () => {
  const fleet = scoped(() => useFleetWorkspace(vi.fn()));
  await flushPromises();
  vi.mocked(api.updateVehicle).mockResolvedValue(vehicle);
  vi.mocked(api.unassignVehicleDriver).mockRejectedValue(new Error('Driver conflict'));
  vi.mocked(api.fetchFleetVehicles).mockResolvedValue([
    { ...vehicle, name: 'Committed update' },
  ]);
  expect(
    await fleet.saveVehicle({
      plateNumber: vehicle.plateNumber,
      name: vehicle.name,
      description: null,
      vehicleType: 'CAR',
      driverId: null,
    }, vehicle.id),
  ).toBe(false);
  expect(fleet.error).toBe('Driver conflict');
  expect(fleet.vehicles[0].name).toBe('Committed update');
  expect(fleet.busy).toBe(false);
});

test('trip vehicle assignment updates detail and list immediately', async () => {
  const toast = vi.fn();
  const fleet = scoped(() => useFleetWorkspace(toast));
  await flushPromises();
  await fleet.selectTrip(1);
  const replacement = { ...vehicle, id: 2, plateNumber: '51B99999', name: 'Replacement' };
  const saved = {
    ...detail(1),
    trip: { ...trip(1), vehicleId: 2, vehiclePlateNumber: replacement.plateNumber },
  };
  vi.mocked(api.assignTripVehicle).mockResolvedValue(saved);

  expect(await fleet.updateTripVehicle(1, 2)).toBe(true);
  expect(api.assignTripVehicle).toHaveBeenCalledWith(1, 2);
  expect(fleet.detail?.trip.vehicleId).toBe(2);
  expect(fleet.trips.find((item) => item.id === 1)?.vehiclePlateNumber).toBe(
    replacement.plateNumber,
  );
  expect(toast).toHaveBeenCalledWith('Đã cập nhật xe thực hiện chuyến.');
});

test('check-ins use the newer revision and hide a previous trip immediately', async () => {
  const live = shallowRef({
    ...snapshot(),
    checkIns: [{ tripId: 1, revision: 3, nextStopSequence: 1, awaitingExit: false, visits: [] }],
  });
  const id = shallowRef<number | null>(1);
  vi.mocked(fetchTripCheckIns).mockResolvedValue({
    tripId: 1,
    revision: 2,
    nextStopSequence: 1,
    awaitingExit: false,
    visits: [],
  });
  const result = scoped(() => useTripCheckIns(id, live));
  await flushPromises();
  expect(result.data?.revision).toBe(3);
  vi.mocked(fetchTripCheckIns).mockReturnValue(new Promise(() => {}));
  id.value = 2;
  expect(result.data).toBeNull();
  await nextTick();
  expect(result.loading).toBe(true);
});

test('trip detail shows wall-clock check-in and labels simulator time separately', async () => {
  const actual = '2026-09-24T07:40:00Z',
    simulated = '2026-09-21T02:00:00Z';
  const running = { ...trip(1, 1, 'IN_PROGRESS'), startedAt: actual };
  const stop = {
    sequenceNumber: 1,
    stationId: 1,
    stationName: 'Trạm A',
    latitude: 10.77,
    longitude: 106.7,
    checkinRadiusMeters: 50,
    dwellDurationSeconds: 30,
    arrivalOffsetSeconds: 0,
    departureOffsetSeconds: 30,
    plannedArrivalAt: actual,
    plannedDepartureAt: actual,
  };
  const current = { ...detail(1), trip: running, stops: [stop] };
  const live = {
    ...snapshot([running]),
    checkIns: [
      {
        tripId: 1,
        revision: 1,
        nextStopSequence: null,
        awaitingExit: false,
        visits: [
          {
            id: 1,
            tripId: 1,
            stopSequence: 1,
            source: 'SIMULATOR' as const,
            evidenceKind: 'POINT' as const,
            actualArrivalAt: actual,
            simulatedArrivalAt: simulated,
            detectedAt: actual,
            fromSampleId: null,
            toSampleId: 1,
            evidenceFraction: 1,
            latitude: 10.77,
            longitude: 106.7,
          },
        ],
      },
    ],
  };
  vi.mocked(fetchTripCheckIns).mockResolvedValue(live.checkIns[0]);
  vi.mocked(fetchTripEta).mockResolvedValue({
    tripId: 1,
    routeId: 1,
    calculatedAt: actual,
    source: 'ROUTE_SNAPSHOT',
    status: 'AVAILABLE',
    trafficObservedAt: null,
    trafficFetchedAt: null,
    nextStopSequence: null,
    baselineRemainingSeconds: 0,
    totalRemainingSeconds: 0,
    stops: [],
    affectedSegments: [],
    warning: null,
  });
  const wrapper = mount(TripDetailPanel, {
    props: {
      detail: current,
      loading: false,
      busy: false,
      error: null,
      drivers: [driver],
      vehicles: [vehicle],
      onClose: vi.fn(),
      onRetry: vi.fn(),
      onAction: vi.fn().mockResolvedValue(true),
      onFocusStop: vi.fn(),
      liveSnapshot: live,
    },
    global: { stubs: { RouteRevisionPanel: true } },
  });
  await flushPromises();
  expect(wrapper.text()).toContain(`Ghi nhận thực tế lúc ${displayTripTime(actual)}`);
  expect(wrapper.text()).toContain(`Giờ mô phỏng: ${displayTripTime(simulated)}`);
  expect(wrapper.text()).toContain('Điều phối tức thời');
  expect(wrapper.text()).not.toContain('Xuất phát kế hoạch');
  expect(wrapper.text()).not.toContain('Sửa giờ xuất phát');
  wrapper.unmount();
});

test('fixed-schedule trip keeps planned time labels but cannot edit an occurrence directly', async () => {
  const fixed = {
    ...detail(1),
    trip: {
      ...trip(1),
      dispatchMode: 'FIXED_SCHEDULE' as const,
      scheduleId: 7,
      scheduleName: 'Ca sáng ngày thường',
    },
  };
  vi.mocked(fetchTripCheckIns).mockResolvedValue({
    tripId: 1,
    revision: 0,
    nextStopSequence: null,
    awaitingExit: false,
    visits: [],
  });
  const wrapper = mount(TripDetailPanel, {
    props: {
      detail: fixed,
      loading: false,
      busy: false,
      error: null,
      drivers: [driver],
      vehicles: [vehicle],
      onClose: vi.fn(),
      onRetry: vi.fn(),
      onAction: vi.fn().mockResolvedValue(true),
      onUpdateVehicle: vi.fn().mockResolvedValue(true),
      onFocusStop: vi.fn(),
    },
    global: { stubs: { RouteRevisionPanel: true } },
  });
  await flushPromises();
  expect(wrapper.text()).toContain('Theo lịch cố định');
  expect(wrapper.text()).toContain('Xuất phát kế hoạch');
  expect(wrapper.text()).toContain('Hoàn thành theo lịch');
  expect(wrapper.text()).toContain('Đổi xe');
  expect(wrapper.text()).not.toContain('Sửa giờ xuất phát');
  expect(wrapper.text()).not.toContain('Xóa chuyến');
  wrapper.unmount();
});

test('ETA polls ten seconds after completion and stops timers on disposal', async () => {
  vi.useFakeTimers();
  const value: TripEta = {
    tripId: 1,
    routeId: 1,
    calculatedAt: stamp,
    source: 'ROUTE_SNAPSHOT',
    status: 'AVAILABLE',
    trafficObservedAt: null,
    trafficFetchedAt: null,
    nextStopSequence: null,
    baselineRemainingSeconds: 0,
    totalRemainingSeconds: 0,
    stops: [],
    affectedSegments: [],
    warning: null,
  };
  vi.mocked(fetchTripEta).mockResolvedValue(value);
  const result = scoped(() => useTripEta(1));
  await vi.advanceTimersByTimeAsync(0);
  expect(result.data?.tripId).toBe(1);
  await vi.advanceTimersByTimeAsync(9999);
  expect(fetchTripEta).toHaveBeenCalledTimes(1);
  await vi.advanceTimersByTimeAsync(1);
  expect(fetchTripEta).toHaveBeenCalledTimes(2);
  scopes[0].stop();
  await vi.advanceTimersByTimeAsync(30000);
  expect(fetchTripEta).toHaveBeenCalledTimes(2);
});

test('vehicle form preserves raw plate contract while trimming name/description', async () => {
  const onSave = vi.fn().mockResolvedValue(true);
  const wrapper = mount(VehicleEditor, {
    props: { vehicle: null, drivers: [driver], busy: false, error: null, onSave, onClose: vi.fn() },
  });
  expect(wrapper.find('select').exists()).toBe(false);
  await wrapper.get('input[name=plateNumber]').setValue('51b-123.45');
  await wrapper.get('input[name=name]').setValue(' Car ');
  await wrapper.get('form').trigger('submit');
  expect(onSave).toHaveBeenCalledWith(
    { plateNumber: '51b-123.45', name: 'Car', description: null, vehicleType: 'CAR', driverId: null },
    undefined,
  );
  await wrapper.setProps({ busy: true });
  await wrapper.get('form').trigger('submit');
  expect(onSave).toHaveBeenCalledTimes(1);
  wrapper.unmount();
});

test('driver form validates phone and uppercases license', async () => {
  const onSave = vi.fn().mockResolvedValue(true);
  const wrapper = mount(DriverEditor, {
    props: { driver: null, busy: false, error: null, onSave, onClose: vi.fn() },
  });
  await wrapper.get('input[name=fullName]').setValue(' Driver ');
  await wrapper.get('input[name=phoneNumber]').setValue('bad');
  await wrapper.get('input[name=licenseNumber]').setValue('b2-123');
  await wrapper.get('form').trigger('submit');
  expect(onSave).not.toHaveBeenCalled();
  await wrapper.get('input[name=phoneNumber]').setValue('0901234567');
  await wrapper.get('form').trigger('submit');
  expect(onSave).toHaveBeenCalledWith(
    { fullName: 'Driver', phoneNumber: '0901234567', licenseNumber: 'B2-123' },
    undefined,
  );
  wrapper.unmount();
});

test('trip form inherits assigned driver and clears it when changing to unassigned vehicle', async () => {
  vi.mocked(fetchRoutes).mockResolvedValue([
    { ...route, startStationName: 'A', endStationName: 'B', stopCount: 2 },
  ]);
  const onSave = vi.fn().mockResolvedValue(true);
  const wrapper = mount(TripEditor, {
    props: {
      vehicles: [vehicle, { ...vehicle, id: 2, driver: null }],
      drivers: [driver],
      initialVehicleId: 1,
      busy: false,
      error: null,
      onSave,
      onClose: vi.fn(),
      onManageRoutes: vi.fn(),
    },
  });
  await flushPromises();
  expect(
    (wrapper.get('select[aria-label="Tài xế thực hiện"]').element as HTMLSelectElement).value,
  ).toBe('1');
  await wrapper.get('select[aria-label="Xe thực hiện *"]').setValue('2');
  await wrapper.get('select[aria-label="Tuyến đường *"]').setValue('1');
  expect(wrapper.find('input[type=datetime-local]').exists()).toBe(false);
  await wrapper.get('form').trigger('submit');
  expect(onSave).toHaveBeenCalledWith({ vehicleId: 2, routeId: 1, driverId: null });
  wrapper.unmount();
});

test('preselected route is the trip draft baseline and can be closed without a false unsaved warning', async () => {
  vi.mocked(fetchRoutes).mockResolvedValue([
    { ...route, startStationName: 'A', endStationName: 'B', stopCount: 2 },
  ]);
  const onClose = vi.fn();
  const wrapper = mount(TripEditor, {
    props: {
      vehicles: [vehicle],
      drivers: [driver],
      initialVehicleId: null,
      initialRouteId: route.id,
      busy: false,
      error: null,
      onSave: vi.fn().mockResolvedValue(true),
      onClose,
      onManageRoutes: vi.fn(),
    },
  });
  await flushPromises();
  expect(
    (wrapper.get('select[aria-label="Tuyến đường *"]').element as HTMLSelectElement).value,
  ).toBe(String(route.id));
  await wrapper.get('[aria-label="Đóng biểu mẫu chuyến"]').trigger('click');
  expect(onClose).toHaveBeenCalledTimes(1);
  expect(wrapper.find('dialog').exists()).toBe(false);
  wrapper.unmount();
});
