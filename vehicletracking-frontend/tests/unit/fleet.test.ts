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
import type { Driver, FleetVehicle, TripDetail, TripSummary } from '@/features/fleet/types/fleet';
import type { OperationsSnapshot } from '@/features/tracking/types/operations';
import type { TripEta } from '@/features/fleet/types/eta';

vi.mock('@/features/fleet/api/fleet', () => ({ fetchFleetVehicles: vi.fn(), fetchDrivers: vi.fn(), fetchTrips: vi.fn(), fetchTrip: vi.fn(), createVehicle: vi.fn(), updateVehicle: vi.fn(), assignVehicleDriver: vi.fn(), unassignVehicleDriver: vi.fn(), deactivateVehicle: vi.fn(), createDriver: vi.fn(), updateDriver: vi.fn(), deactivateDriver: vi.fn(), createTrip: vi.fn(), changeTripStatus: vi.fn(), updateTrip: vi.fn(), deleteTrip: vi.fn(), assignTripDriver: vi.fn(), unassignTripDriver: vi.fn() }));
vi.mock('@/features/fleet/api/eta', () => ({ fetchTripEta: vi.fn() }));
vi.mock('@/features/fleet/api/checkins', () => ({ fetchTripCheckIns: vi.fn() }));
vi.mock('@/features/routes/api/routes', () => ({ fetchRoutes: vi.fn() }));
const stamp = '2026-09-22T01:00:00Z';
const driver: Driver = { id: 1, fullName: 'Fixture Driver', phoneNumber: '0901234567', licenseNumber: 'B2-123', active: true, createdAt: stamp, updatedAt: stamp };
const vehicle: FleetVehicle = { id: 1, name: 'Fixture Car', plateNumber: '51B12345', description: null, vehicleType: 'CAR', active: true, driver, createdAt: stamp, updatedAt: stamp };
const trip = (id: number, attemptNumber = 1, status: TripSummary['status'] = 'SCHEDULED'): TripSummary => ({ id, attemptNumber, status, vehicleId: 1, vehiclePlateNumber: vehicle.plateNumber, vehicleType: 'CAR', routeId: 1, routeName: 'Fixture route', scheduledDepartureAt: stamp, plannedEndAt: stamp, startedAt: null, endedAt: null, createdAt: stamp, driver });
const route: TripDetail['route'] = { id: 1, name: 'Fixture route', transportMode: 'CAR', routingProvider: 'HERE', totalDistanceMeters: 1000, estimatedTravelDurationSeconds: 600, baseTravelDurationSeconds: 600, totalDwellDurationSeconds: 60, estimatedTripDurationSeconds: 660, estimatedDepartureAt: stamp, calculatedAt: stamp, createdAt: stamp, stops: [], sections: [] };
const detail = (id: number, attempt = 1): TripDetail => ({ trip: trip(id, attempt), stops: [], route });
const snapshot = (trips: TripSummary[] = []): OperationsSnapshot => ({ serverTime: stamp, trips, positions: [], simulations: [], checkIns: [], notifications: [] });
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(r => { resolve = r; }); return { promise, resolve }; }
const scopes: ReturnType<typeof effectScope>[] = [];
function scoped<T>(factory: () => T): T { const scope = effectScope(); scopes.push(scope); return scope.run(factory)!; }
beforeEach(() => {
  vi.resetAllMocks(); vi.mocked(api.fetchFleetVehicles).mockResolvedValue([vehicle]); vi.mocked(api.fetchDrivers).mockResolvedValue([driver]); vi.mocked(api.fetchTrips).mockResolvedValue([trip(1), trip(2)]);
  vi.mocked(api.fetchTrip).mockImplementation(id => Promise.resolve(detail(id)));
});
afterEach(() => { scopes.splice(0).forEach(scope => scope.stop()); vi.useRealTimers(); });

test('detail A cannot overwrite B; disposal aborts outstanding reads', async () => {
  const a = deferred<TripDetail>(), b = deferred<TripDetail>();
  vi.mocked(api.fetchTrip).mockImplementation(id => id === 1 ? a.promise : b.promise);
  const fleet = scoped(() => useFleetWorkspace(vi.fn())); await flushPromises();
  const first = fleet.selectTrip(1); const signalA = vi.mocked(api.fetchTrip).mock.calls[0][1]!;
  const second = fleet.selectTrip(2); expect(signalA.aborted).toBe(true);
  b.resolve(detail(2)); await second; a.resolve(detail(1)); await first;
  expect(fleet.detail?.trip.id).toBe(2);
  const signalB = vi.mocked(api.fetchTrip).mock.calls[1][1]!; scopes[0].stop(); expect(signalB.aborted).toBe(true);
});

test('only the latest selection is applied after an in-flight mutation', async () => {
  const pending = deferred<Driver>(); vi.mocked(api.createDriver).mockReturnValue(pending.promise);
  const selection = shallowRef<{ tripId: number | null } | null>(null);
  const fleet = scoped(() => useFleetWorkspace(vi.fn(), undefined, selection)); await flushPromises();
  const saving = fleet.saveDriver(driver); expect(fleet.busy).toBe(true);
  selection.value = { tripId: 1 }; await nextTick(); selection.value = { tripId: 2 }; await nextTick();
  expect(api.fetchTrip).not.toHaveBeenCalled();
  pending.resolve(driver); await saving; await flushPromises();
  expect(api.fetchTrip).toHaveBeenCalledTimes(1); expect(fleet.detail?.trip.id).toBe(2);
  fleet.close(); await nextTick(); expect(fleet.screen.kind).toBe('list');
});

test('new replay hides old detail and reloads exactly once', async () => {
  const live = shallowRef(snapshot([trip(1)])); const pending = deferred<TripDetail>();
  const fleet = scoped(() => useFleetWorkspace(vi.fn(), live)); await flushPromises(); await fleet.selectTrip(1);
  vi.mocked(api.fetchTrip).mockReturnValueOnce(pending.promise);
  live.value = snapshot([trip(1, 2)]); await nextTick(); expect(fleet.detail).toBeNull();
  pending.resolve(detail(1, 2)); await flushPromises(); expect(fleet.detail?.trip.attemptNumber).toBe(2); expect(api.fetchTrip).toHaveBeenCalledTimes(2);
});

test('locally completed trip is not reverted by older realtime status', async () => {
  const live = shallowRef(snapshot([trip(1, 1, 'IN_PROGRESS')])); const fleet = scoped(() => useFleetWorkspace(vi.fn(), live));
  await flushPromises(); await fleet.selectTrip(1);
  vi.mocked(api.changeTripStatus).mockResolvedValue({ ...detail(1), trip: trip(1, 1, 'COMPLETED') });
  await fleet.transition('complete'); live.value = snapshot([trip(1, 1, 'SCHEDULED')]); await nextTick();
  expect(fleet.detail?.trip.status).toBe('COMPLETED'); expect(fleet.trips.find(t => t.id === 1)?.status).toBe('COMPLETED');
});

test('vehicle assignment partial failure refreshes committed vehicle state', async () => {
  const fleet = scoped(() => useFleetWorkspace(vi.fn())); await flushPromises();
  vi.mocked(api.createVehicle).mockResolvedValue({ ...vehicle, driver: null });
  vi.mocked(api.assignVehicleDriver).mockRejectedValue(new Error('Driver conflict'));
  vi.mocked(api.fetchFleetVehicles).mockResolvedValue([{ ...vehicle, name: 'Committed update', driver: null }]);
  expect(await fleet.saveVehicle({ plateNumber: vehicle.plateNumber, name: vehicle.name, description: null, vehicleType: 'CAR', driverId: 1 })).toBe(false);
  expect(fleet.error).toBe('Driver conflict'); expect(fleet.vehicles[0].name).toBe('Committed update'); expect(fleet.busy).toBe(false);
});

test('check-ins use the newer revision and hide a previous trip immediately', async () => {
  const live = shallowRef({ ...snapshot(), checkIns: [{ tripId: 1, revision: 3, nextStopSequence: 1, awaitingExit: false, visits: [] }] });
  const id = shallowRef<number | null>(1);
  vi.mocked(fetchTripCheckIns).mockResolvedValue({ tripId: 1, revision: 2, nextStopSequence: 1, awaitingExit: false, visits: [] });
  const result = scoped(() => useTripCheckIns(id, live)); await flushPromises(); expect(result.data?.revision).toBe(3);
  vi.mocked(fetchTripCheckIns).mockReturnValue(new Promise(() => {})); id.value = 2;
  expect(result.data).toBeNull(); await nextTick(); expect(result.loading).toBe(true);
});

test('ETA polls ten seconds after completion and stops timers on disposal', async () => {
  vi.useFakeTimers();
  const value: TripEta = { tripId: 1, routeId: 1, calculatedAt: stamp, source: 'ROUTE_SNAPSHOT', status: 'AVAILABLE', trafficObservedAt: null, trafficFetchedAt: null, nextStopSequence: null, baselineRemainingSeconds: 0, totalRemainingSeconds: 0, stops: [], affectedSegments: [], warning: null };
  vi.mocked(fetchTripEta).mockResolvedValue(value);
  const result = scoped(() => useTripEta(1)); await vi.advanceTimersByTimeAsync(0); expect(result.data?.tripId).toBe(1);
  await vi.advanceTimersByTimeAsync(9999); expect(fetchTripEta).toHaveBeenCalledTimes(1);
  await vi.advanceTimersByTimeAsync(1); expect(fetchTripEta).toHaveBeenCalledTimes(2);
  scopes[0].stop(); await vi.advanceTimersByTimeAsync(30000); expect(fetchTripEta).toHaveBeenCalledTimes(2);
});

test('vehicle form preserves raw plate contract while trimming name/description', async () => {
  const onSave = vi.fn().mockResolvedValue(true);
  const wrapper = mount(VehicleEditor, { props: { vehicle: null, drivers: [driver], busy: false, error: null, onSave, onClose: vi.fn() } });
  await wrapper.get('input[name=plateNumber]').setValue('51b-123.45'); await wrapper.get('input[name=name]').setValue(' Car ');
  await wrapper.get('select').setValue('1'); await wrapper.get('form').trigger('submit');
  expect(onSave).toHaveBeenCalledWith({ plateNumber: '51b-123.45', name: 'Car', description: null, vehicleType: 'CAR', driverId: 1 }, undefined);
  await wrapper.setProps({ busy: true }); await wrapper.get('form').trigger('submit'); expect(onSave).toHaveBeenCalledTimes(1); wrapper.unmount();
});

test('driver form validates phone and uppercases license', async () => {
  const onSave = vi.fn().mockResolvedValue(true);
  const wrapper = mount(DriverEditor, { props: { driver: null, busy: false, error: null, onSave, onClose: vi.fn() } });
  await wrapper.get('input[name=fullName]').setValue(' Driver '); await wrapper.get('input[name=phoneNumber]').setValue('bad'); await wrapper.get('input[name=licenseNumber]').setValue('b2-123');
  await wrapper.get('form').trigger('submit'); expect(onSave).not.toHaveBeenCalled();
  await wrapper.get('input[name=phoneNumber]').setValue('0901234567'); await wrapper.get('form').trigger('submit');
  expect(onSave).toHaveBeenCalledWith({ fullName: 'Driver', phoneNumber: '0901234567', licenseNumber: 'B2-123' }, undefined); wrapper.unmount();
});

test('trip form inherits assigned driver and clears it when changing to unassigned vehicle', async () => {
  vi.mocked(fetchRoutes).mockResolvedValue([{ ...route, startStationName: 'A', endStationName: 'B', stopCount: 2 }]);
  const onSave = vi.fn().mockResolvedValue(true);
  const wrapper = mount(TripEditor, { props: { vehicles: [vehicle, { ...vehicle, id: 2, driver: null }], drivers: [driver], initialVehicleId: 1, busy: false, error: null, onSave, onClose: vi.fn(), onManageRoutes: vi.fn() } });
  await flushPromises(); expect((wrapper.get('select[aria-label="Tài xế thực hiện"]').element as HTMLSelectElement).value).toBe('1');
  await wrapper.get('select[aria-label="Xe thực hiện *"]').setValue('2'); await wrapper.get('select[aria-label="Tuyến đường *"]').setValue('1');
  await wrapper.get('input[type=datetime-local]').setValue('2026-09-22T08:15'); await wrapper.get('form').trigger('submit');
  expect(onSave).toHaveBeenCalledWith({ vehicleId: 2, routeId: 1, driverId: null, scheduledDepartureAt: new Date('2026-09-22T08:15').toISOString() }); wrapper.unmount();
});
