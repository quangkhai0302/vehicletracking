import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { effectScope, nextTick, shallowRef } from 'vue';
import { flushPromises } from '@vue/test-utils';
import L from 'leaflet';
import * as operations from '@/features/tracking/api/operations';
import * as fleet from '@/features/fleet/api/fleet';
import * as traffic from '@/features/traffic/api/hereTraffic';
import * as stations from '@/features/stations/api/stations';
import { useLiveOperations } from '@/features/tracking/composables/useLiveOperations';
import { useSimulator } from '@/features/simulation/composables/useSimulator';
import { useSelectedVehicleRoute } from '@/features/routes/composables/useSelectedVehicleRoute';
import { useRouteTraffic } from '@/features/routes/composables/useRouteTraffic';
import { useTraffic } from '@/features/traffic/composables/useTraffic';
import { usePlannedVehicleAnchors } from '@/features/tracking/composables/usePlannedVehicleAnchors';
import { useSimulationFleet } from '@/features/simulation/composables/useSimulationFleet';
import { useStationWorkspace } from '@/features/stations/composables/useStationWorkspace';
import { useVehicleMarkers } from '@/features/tracking/composables/useVehicleMarkers';
import { useCompactLayout } from '@/shared/composables/useCompactLayout';
import type { OperationsSnapshot, SimulationRun } from '@/features/tracking/types/operations';
import type { TripDetail, TripSummary } from '@/features/fleet/types/fleet';
import type { Station } from '@/features/stations/types/station';

vi.mock('@/features/tracking/api/operations', () => ({ fetchOperations: vi.fn(), subscribeOperations: vi.fn(), controlSimulation: vi.fn() }));
vi.mock('@/features/fleet/api/fleet', () => ({ fetchTrip: vi.fn(), fetchTripRoute: vi.fn() }));
vi.mock('@/features/traffic/api/hereTraffic', () => ({ fetchHereTrafficFlow: vi.fn(), fetchHereIncidents: vi.fn() }));
vi.mock('@/features/stations/api/stations', () => ({ fetchStations: vi.fn(), createStation: vi.fn(), updateStation: vi.fn(), deleteStation: vi.fn() }));

const stamp = '2026-09-22T01:00:00Z';
const trip = (id = 1, attemptNumber = 1): TripSummary => ({ id, attemptNumber, vehicleId: id, vehiclePlateNumber: `51B0000${id}`, vehicleType: 'CAR',
  routeId: 1, routeName: 'Fixture route', scheduledDepartureAt: stamp, plannedEndAt: stamp, startedAt: null, endedAt: null, createdAt: stamp, driver: null, status: 'SCHEDULED' });
const route: TripDetail['route'] = { id: 1, name: 'Fixture route', transportMode: 'CAR', routingProvider: 'HERE', totalDistanceMeters: 1000,
  estimatedTravelDurationSeconds: 600, baseTravelDurationSeconds: 600, totalDwellDurationSeconds: 60, estimatedTripDurationSeconds: 660,
  estimatedDepartureAt: stamp, calculatedAt: stamp, createdAt: stamp, stops: [], sections: [] };
const detail = (id = 1, attempt = 1): TripDetail => ({ trip: trip(id, attempt), route, stops: [{ stationId: 1, stationName: 'First stop', sequenceNumber: 1, latitude: 10.8, longitude: 106.7, dwellDurationSeconds: 60, checkinRadiusMeters: 100, arrivalOffsetSeconds: 0, departureOffsetSeconds: 60, plannedArrivalAt: stamp, plannedDepartureAt: stamp }] });
const snapshot = (trips = [trip()], time = stamp): OperationsSnapshot => ({ serverTime: time, trips, positions: [], simulations: [], checkIns: [], notifications: [] });
const run = (attemptNumber = 1): SimulationRun => ({ id: 1, tripId: 1, attemptNumber, status: 'RUNNING', multiplier: 1, elapsedSeconds: 0, durationSeconds: 600,
  simulatedAt: stamp, updatedAt: stamp, errorMessage: null, replacementTripId: null, frame: null });
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(r => { resolve = r; }); return { promise, resolve }; }
const scopes: ReturnType<typeof effectScope>[] = [];
const maps: L.Map[] = [];
function scoped<T>(factory: () => T) { const scope = effectScope(); scopes.push(scope); return scope.run(factory)!; }
function mapInstance() { const host = document.createElement('div'); document.body.append(host); const map = L.map(host).setView([10.8, 106.7], 14); maps.push(map); return map; }
beforeEach(() => {
  vi.resetAllMocks(); vi.mocked(operations.fetchOperations).mockResolvedValue(snapshot());
  vi.mocked(operations.subscribeOperations).mockImplementation(() => vi.fn());
  vi.mocked(fleet.fetchTrip).mockImplementation(id => Promise.resolve(detail(id)));
  vi.mocked(fleet.fetchTripRoute).mockResolvedValue(route);
  const envelope = { source: 'HERE_LIVE' as const, status: 'AVAILABLE' as const, observedAt: stamp, fetchedAt: stamp, ageSeconds: 0, warning: null, results: [] };
  vi.mocked(traffic.fetchHereTrafficFlow).mockResolvedValue(envelope);
  vi.mocked(traffic.fetchHereIncidents).mockResolvedValue(envelope);
  vi.mocked(stations.fetchStations).mockResolvedValue([]);
});
afterEach(() => {
  scopes.splice(0).forEach(scope => scope.stop()); maps.splice(0).forEach(map => map.remove()); document.body.replaceChildren();
  vi.useRealTimers(); vi.unstubAllGlobals();
});

test('live SSE wins over older HTTP bootstrap; reconnect and disposal release resources', async () => {
  vi.useFakeTimers(); vi.setSystemTime(stamp);
  const pending = deferred<OperationsSnapshot>(); vi.mocked(operations.fetchOperations).mockReturnValue(pending.promise);
  const state = scoped(useLiveOperations), subscribe = vi.mocked(operations.subscribeOperations);
  const push = subscribe.mock.calls[0][0], stop = subscribe.mock.results[0].value;
  const newer = snapshot([trip(2)], '2026-09-22T01:00:03Z'); push(newer); pending.resolve(snapshot()); await flushPromises();
  expect(state.snapshot).toEqual(newer); expect(state.connection).toBe('live');
  await vi.advanceTimersByTimeAsync(6000); expect(state.connection).toBe('reconnecting');
  state.reconnect(); await nextTick(); expect(stop).toHaveBeenCalledTimes(1);
  expect(vi.mocked(operations.fetchOperations).mock.calls[0][0]?.aborted).toBe(true);
  expect(subscribe).toHaveBeenCalledTimes(2); scopes[0].stop();
  expect(subscribe.mock.results[1].value).toHaveBeenCalledTimes(1); expect(vi.getTimerCount()).toBe(0);
  push(snapshot([trip(3)], '2026-09-22T01:01:00Z')); expect(state.snapshot).not.toEqual(snapshot([trip(3)], '2026-09-22T01:01:00Z'));
});

test('simulator guards duplicate commands/selection while busy and hides old replay detail', async () => {
  const live = shallowRef({ ...snapshot(), simulations: [run()] }), toast = vi.fn();
  const state = scoped(() => useSimulator(live, toast)); state.select(1); await flushPromises();
  expect(state.detail?.trip.attemptNumber).toBe(1);
  const pending = deferred<SimulationRun>(), replay = deferred<TripDetail>();
  vi.mocked(operations.controlSimulation).mockReturnValue(pending.promise); vi.mocked(fleet.fetchTrip).mockReturnValue(replay.promise);
  const reset = state.command('reset'); expect(state.busy).toBe(true); state.select(2);
  expect(state.tripId).toBe(1); expect(await state.command('play')).toBe(false);
  pending.resolve(run(2)); await reset; await nextTick();
  expect(state.run?.attemptNumber).toBe(2); expect(state.detail).toBeNull(); expect(state.loading).toBe(true);
  replay.resolve(detail(1, 2)); await flushPromises(); expect(state.detail?.trip.attemptNumber).toBe(2);
  expect(operations.controlSimulation).toHaveBeenCalledTimes(1); expect(fleet.fetchTrip).toHaveBeenCalledTimes(2);
  live.value = { ...snapshot([trip(1, 2)]), simulations: [{ ...run(2), updatedAt: '2026-09-22T01:00:10Z', status: 'PAUSED' }] };
  expect(state.run?.status).toBe('PAUSED'); expect(toast).toHaveBeenCalledTimes(1);
});

test('simulator ignores an in-flight command response after disposal', async () => {
  const toast = vi.fn(), pending = deferred<SimulationRun>(); vi.mocked(operations.controlSimulation).mockReturnValue(pending.promise);
  const state = scoped(() => useSimulator(snapshot(), toast)); state.select(1); await nextTick();
  const command = state.command('play'); scopes[0].stop(); pending.resolve(run()); await command;
  expect(toast).not.toHaveBeenCalled(); expect(state.run).toBeNull();
  expect(vi.mocked(fleet.fetchTrip).mock.calls[0][1]?.aborted).toBe(true);
});

test('selected route hides immediately on revision/selection changes and aborts stale requests', async () => {
  const id = shallowRef<number | null>(1), revision = shallowRef('1:0'), error = vi.fn();
  const state = scoped(() => useSelectedVehicleRoute(id, error, revision)); await flushPromises(); expect(state.value?.id).toBe(1);
  const a = deferred<typeof route>(), b = deferred<typeof route>();
  vi.mocked(fleet.fetchTripRoute).mockImplementationOnce(() => a.promise).mockImplementationOnce(() => b.promise);
  revision.value = '2:0'; expect(state.value).toBeNull(); await nextTick();
  id.value = 2; await nextTick(); expect(vi.mocked(fleet.fetchTripRoute).mock.calls[1][1]?.aborted).toBe(true);
  b.resolve({ ...route, id: 2 }); await flushPromises(); a.resolve(route); await flushPromises();
  expect(state.value?.id).toBe(2); expect(error).not.toHaveBeenCalled();
});

test('route traffic dwell avoids transient hover requests and caches successful responses', async () => {
  vi.useFakeTimers(); const key = shallowRef<string | null>('a'), state = scoped(() => useRouteTraffic(key));
  await vi.advanceTimersByTimeAsync(100); key.value = 'b'; await nextTick();
  await vi.advanceTimersByTimeAsync(199); expect(traffic.fetchHereTrafficFlow).not.toHaveBeenCalled();
  await vi.advanceTimersByTimeAsync(1); expect(state.data?.key).toBe('b');
  key.value = null; expect(state.data).toBeNull(); await nextTick(); key.value = 'b'; await nextTick(); await vi.advanceTimersByTimeAsync(200);
  expect(traffic.fetchHereTrafficFlow).toHaveBeenCalledTimes(1);
  state.retry(); await nextTick(); await vi.advanceTimersByTimeAsync(200); expect(traffic.fetchHereTrafficFlow).toHaveBeenCalledTimes(2);
  scopes[0].stop(); expect(vi.getTimerCount()).toBe(0);
});

test('failed traffic refresh replaces cached success instead of retaining a live badge', async () => {
  vi.useFakeTimers(); const state = scoped(() => useRouteTraffic('fixture-bbox'));
  await vi.advanceTimersByTimeAsync(200); expect(state.data?.flowError).toBe(false);
  vi.mocked(traffic.fetchHereTrafficFlow).mockRejectedValue(new Error('Offline'));
  await vi.advanceTimersByTimeAsync(60000); expect(state.data?.flowError).toBe(true); expect(state.data?.flow).toBeNull();
});

test('viewport traffic reuses bounds; disabling aborts requests and removes listener/timers', async () => {
  vi.useFakeTimers(); const map = mapInstance(), enabled = shallowRef(true);
  const state = scoped(() => useTraffic(shallowRef(map), enabled, true)); await flushPromises();
  expect(traffic.fetchHereTrafficFlow).toHaveBeenCalledTimes(1); map.fire('moveend'); await vi.advanceTimersByTimeAsync(350);
  expect(traffic.fetchHereTrafficFlow).toHaveBeenCalledTimes(1);
  enabled.value = false; expect(state.flow).toBeNull(); await nextTick();
  expect(map.listens('moveend')).toBe(false); expect(vi.getTimerCount()).toBe(0);
  expect(vi.mocked(traffic.fetchHereTrafficFlow).mock.calls[0][1]?.aborted).toBe(true);
});

test('planned anchors replace old finished telemetry but yield to active telemetry for the vehicle', async () => {
  const old = { ...trip(2), vehicleId: 1, status: 'COMPLETED' as const };
  const live = shallowRef<OperationsSnapshot>({ ...snapshot([trip(), old]), positions: [{ id: 1, eventId: 'old', tripId: 2, vehicleId: 1, latitude: 10.7, longitude: 106.6,
    source: 'GPS', recordedAt: stamp, receivedAt: stamp, simulatedAt: null, speedKmh: 0, heading: 0, accuracyMeters: 0 }] });
  const anchors = scoped(() => usePlannedVehicleAnchors(live)); await flushPromises();
  expect(anchors.value).toEqual([{ vehicleId: 1, tripId: 1, vehiclePlateNumber: '51B00001', vehicleType: 'CAR', latitude: 10.8, longitude: 106.7 }]);
  live.value = { ...live.value, trips: [trip(), { ...old, status: 'IN_PROGRESS' }] }; expect(anchors.value).toEqual([]);
});

test('simulation fleet keeps valid station markers even when route geometry is invalid', async () => {
  const live = shallowRef(snapshot()), enabled = shallowRef(true);
  const state = scoped(() => useSimulationFleet(live, enabled)); await flushPromises();
  expect(state.previews).toHaveLength(1); expect(state.routes).toHaveLength(0); expect(state.routeFailures[0].message).toBe('Hình học tuyến không hợp lệ.');
  enabled.value = false; expect(state.previews).toEqual([]); expect(state.routeFailures).toEqual([]); await nextTick();
  expect(vi.mocked(fleet.fetchTrip).mock.calls[0][1]?.aborted).toBe(true);
});

test('station edit retains mutation intent after resetting the drawer and ignores post-disposal completions', async () => {
  const station: Station = { id: 1, name: 'Fixture station', address: null, latitude: 10.8, longitude: 106.7, checkinRadiusMeters: 100, active: true, createdAt: stamp, updatedAt: stamp };
  const onToast = vi.fn(), onStationUpdated = vi.fn(), focusLocation = vi.fn();
  vi.mocked(stations.updateStation).mockResolvedValue(station);
  const state = scoped(() => useStationWorkspace({ onToast, onStationUpdated, focusLocation, onPickStart: vi.fn(), onPickEnd: vi.fn() }));
  await flushPromises(); state.handleBeginEdit(station); await state.handleSaveStation(station);
  expect(state.formMode).toBe('closed'); expect(onStationUpdated).toHaveBeenCalledTimes(1); expect(onToast).toHaveBeenCalledWith('Đã cập nhật trạm “Fixture station”.');
  const pending = deferred<Station>(); vi.mocked(stations.createStation).mockReturnValue(pending.promise);
  state.handleBeginCreate(); const save = state.handleSaveStation(station); scopes[0].stop(); pending.resolve(station); await save;
  expect(onToast).toHaveBeenCalledTimes(1); expect(state.formMode).toBe('create');
});

test('marker updates reuse Leaflet instances and visibility/disposal removes layers and callbacks', async () => {
  const map = mapInstance(), mapRef = shallowRef<L.Map | null>(map), visible = shallowRef(true), select = vi.fn(), focus = vi.fn();
  const live = shallowRef(snapshot()); const anchors = [{ vehicleId: 1, tripId: 1, vehiclePlateNumber: '51B00001', vehicleType: 'CAR' as const, latitude: 10.8, longitude: 106.7 }];
  scoped(() => useVehicleMarkers(() => ({ mapRef, snapshot: live.value, plannedPositions: anchors, now: Date.parse(stamp), visible: visible.value,
    selectedId: null, following: false, onSelect: select, onFocus: focus })));
  const markers = () => { const result: L.Marker[] = []; map.eachLayer(layer => { if (layer instanceof L.Marker) result.push(layer); }); return result; };
  const first = markers()[0]; expect(markers()).toHaveLength(1); first.fire('click'); expect(select).toHaveBeenCalledWith(1, 1);
  expect(focus).toHaveBeenCalledWith(first.getLatLng());
  live.value = { ...snapshot(), serverTime: '2026-09-22T01:00:01Z' }; await nextTick(); expect(markers()[0]).toBe(first);
  visible.value = false; await nextTick(); expect(markers()).toHaveLength(0); expect(first.listens('click')).toBe(false);
  visible.value = true; await nextTick(); const last = markers()[0]; scopes[0].stop();
  expect(markers()).toHaveLength(0); expect(last.listens('click')).toBe(false);
});

test('compact layout listens to its original media query and disposes its listener', () => {
  const media = { matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() };
  vi.stubGlobal('matchMedia', vi.fn(() => media)); const compact = scoped(useCompactLayout);
  expect(compact.value).toBe(false); media.matches = true; media.addEventListener.mock.calls[0][1](); expect(compact.value).toBe(true);
  scopes[0].stop(); expect(media.removeEventListener).toHaveBeenCalledWith('change', media.addEventListener.mock.calls[0][1]);
});
