import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import L from 'leaflet';
import * as stationApi from '@/features/stations/api/stations';
import MapComponent from '@/features/map/components/MapComponent.vue';
import * as operations from '@/features/tracking/api/operations';
import * as fleetApi from '@/features/fleet/api/fleet';
import * as etaApi from '@/features/fleet/api/eta';
import type { OperationsSnapshot, SimulationRun } from '@/features/tracking/types/operations';
import type { TripDetail } from '@/features/fleet/types/fleet';
import { notifyError } from '@/shared/notifications/toast';
vi.mock('@/features/tracking/api/operations', () => ({
  fetchOperations: vi.fn(),
  subscribeOperations: vi.fn(),
  controlSimulation: vi.fn(),
}));
vi.mock('@/shared/notifications/toast', () => ({
  errorMessage: (reason: unknown, fallback = 'Đã xảy ra lỗi. Vui lòng thử lại.') =>
    reason instanceof Error ? reason.message : fallback,
  notifyError: vi.fn(),
  notifySuccess: vi.fn(),
  notifyWarning: vi.fn(),
  notifyInfo: vi.fn(),
  notifyLegacy: vi.fn(),
}));
const snapshot: OperationsSnapshot = {
  serverTime: '2026-09-23T01:00:00Z',
  positions: [],
  trips: [],
  simulations: [],
  checkIns: [],
  notifications: [],
};
const originalCanvas = Object.getOwnPropertyDescriptor(L.Browser, 'canvas')!,
  originalSvg = Object.getOwnPropertyDescriptor(L.Browser, 'svg')!;
const originalCheckVisibility = Object.getOwnPropertyDescriptor(
  HTMLElement.prototype,
  'checkVisibility',
);
const unmounts: (() => void)[] = [];
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(operations.fetchOperations).mockResolvedValue(snapshot);
  vi.mocked(operations.subscribeOperations).mockImplementation((callback) => {
    callback(snapshot);
    return vi.fn();
  });
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      const body = url.includes('/traffic/')
        ? {
            source: 'HERE_LIVE',
            status: 'AVAILABLE',
            observedAt: snapshot.serverTime,
            fetchedAt: snapshot.serverTime,
            ageSeconds: 0,
            warning: null,
            results: [],
          }
        : [];
      return new Response(JSON.stringify(body), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    }),
  );
  vi.stubGlobal(
    'matchMedia',
    vi.fn(() => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() })),
  );
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe = vi.fn();
      disconnect = vi.fn();
    },
  );
  // jsdom has no layout/visibility implementation; model the hidden panel contract.
  Object.defineProperty(HTMLElement.prototype, 'checkVisibility', {
    configurable: true,
    value(this: HTMLElement) {
      return !this.closest('[hidden]');
    },
  });
  Object.defineProperty(L.Browser, 'canvas', { ...originalCanvas, value: false });
  Object.defineProperty(L.Browser, 'svg', { ...originalSvg, value: true });
});
afterEach(() => {
  unmounts.splice(0).forEach((unmount) => unmount());
  document.body.replaceChildren();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  Object.defineProperty(L.Browser, 'canvas', originalCanvas);
  Object.defineProperty(L.Browser, 'svg', originalSvg);
  if (originalCheckVisibility)
    Object.defineProperty(HTMLElement.prototype, 'checkVisibility', originalCheckVisibility);
  else Reflect.deleteProperty(HTMLElement.prototype, 'checkVisibility');
});

function liveControlsFixture(source: 'GPS' | 'SIMULATOR' = 'SIMULATOR', id = 42) {
  const trip: TripDetail['trip'] = {
    id, attemptNumber: 1, vehicleId: id, vehiclePlateNumber: `51B-${id}`, vehicleType: 'CAR',
    routeId: 3, routeName: 'Bến Thành → Suối Tiên', status: 'IN_PROGRESS',
    scheduledDepartureAt: snapshot.serverTime, plannedEndAt: snapshot.serverTime,
    startedAt: snapshot.serverTime, endedAt: null, createdAt: snapshot.serverTime,
    dispatchMode: 'ON_DEMAND', scheduleId: null, scheduleName: null,
    driver: { id, fullName: `Tài xế ${id}`, phoneNumber: '0900000000', licenseNumber: 'B2-fixture' },
  };
  const stops: TripDetail['stops'] = ['Bến Thành', 'Suối Tiên'].map((stationName, i) => ({
    sequenceNumber: i + 1, stationId: i + 1, stationName,
    latitude: 10.77 + i * 0.1, longitude: 106.7 + i * 0.1,
    checkinRadiusMeters: 50, dwellDurationSeconds: 30,
    arrivalOffsetSeconds: i * 900, departureOffsetSeconds: i * 900 + 30,
    plannedArrivalAt: snapshot.serverTime, plannedDepartureAt: snapshot.serverTime,
  }));
  const detail: TripDetail = {
    trip, stops, route: {
      id: 3, name: trip.routeName, transportMode: 'CAR', routingProvider: 'HERE',
      totalDistanceMeters: 5000, estimatedTravelDurationSeconds: 900,
      baseTravelDurationSeconds: 900, totalDwellDurationSeconds: 60,
      estimatedTripDurationSeconds: 960, estimatedDepartureAt: snapshot.serverTime,
      calculatedAt: snapshot.serverTime, createdAt: snapshot.serverTime,
      stops: stops.map((stop, i) => ({ ...stop, role: i === 0 ? 'START' : 'END',
        distanceFromPreviousMeters: i * 5000, travelDurationFromPreviousSeconds: i * 900 })),
      sections: [], shapingPoints: [],
    },
  };
  const run: SimulationRun = {
    id, tripId: id, attemptNumber: 1, status: 'RUNNING', multiplier: 1,
    elapsedSeconds: 30, durationSeconds: 960, simulatedAt: snapshot.serverTime,
    updatedAt: snapshot.serverTime, errorMessage: null, replacementTripId: null, frame: null,
  };
  const liveSnapshot: OperationsSnapshot = {
    ...snapshot, trips: [trip], simulations: source === 'SIMULATOR' ? [run] : [],
    positions: [{
      id, eventId: `fixture-${id}`, vehicleId: id, tripId: id, attemptNumber: 1,
      recordedAt: snapshot.serverTime, receivedAt: snapshot.serverTime,
      simulatedAt: source === 'SIMULATOR' ? snapshot.serverTime : null,
      latitude: 10.78, longitude: 106.71, speedKmh: 30, heading: 90, accuracyMeters: 5, source,
    }],
  };
  return { detail, liveSnapshot, run };
}

function mockLiveControlsEta() {
  vi.spyOn(etaApi, 'fetchTripEta').mockImplementation(async tripId => ({
    tripId, routeId: 3, calculatedAt: snapshot.serverTime, source: 'ROUTE_SNAPSHOT',
    status: 'AVAILABLE', trafficObservedAt: null, trafficFetchedAt: null, nextStopSequence: 2,
    baselineRemainingSeconds: 840, totalRemainingSeconds: 840, stops: [], affectedSegments: [], warning: null,
  }));
}

test.each([false, true])(
  'live monitoring switches stops/controls for the same running trip without new map, SSE or commands (compact: %s)',
  async (compact) => {
    const { detail, liveSnapshot, run } = liveControlsFixture();
    vi.mocked(operations.fetchOperations).mockResolvedValue(liveSnapshot);
    vi.mocked(operations.subscribeOperations).mockImplementation((callback) => {
      callback(liveSnapshot); return vi.fn();
    });
    const fetchDetail = vi.spyOn(fleetApi, 'fetchTrip').mockResolvedValue(detail);
    const fetchRoute = vi.spyOn(fleetApi, 'fetchTripRoute').mockResolvedValue(detail.route);
    mockLiveControlsEta();
    const remove = vi.spyOn(L.Map.prototype, 'remove');
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: compact,
      addEventListener: vi.fn(), removeEventListener: vi.fn() })));
    const onWorkspaceChange = vi.fn();
    const wrapper = mount(MapComponent, {
      props: { initialTripId: detail.trip.id, onWorkspaceChange },
      global: { stubs: { RouterLink: true } }, attachTo: document.body,
    });
    unmounts.push(() => wrapper.unmount());
    await flushPromises();
    const canvas = wrapper.get('#main-map').element;
    const [stops, controls] = wrapper.findAll('.tracking-panel-switch button');
    const detailCalls = fetchDetail.mock.calls.length, routeCalls = fetchRoute.mock.calls.length;
    expect(stops.text()).toBe('Danh sách trạm');
    expect(controls.text()).toBe('Bảng điều khiển');
    expect(stops.attributes('aria-pressed')).toBe('true');
    expect(wrapper.get('#tracking-stops-panel').attributes('hidden')).toBeUndefined();
    expect(wrapper.get('#vehicle-controls-panel').attributes('hidden')).toBeDefined();

    await controls.trigger('click');
    expect(controls.attributes('aria-pressed')).toBe('true');
    expect(wrapper.get('.context-drawer').attributes('hidden')).toBeUndefined();
    expect(wrapper.get('#vehicle-controls-panel').attributes('hidden')).toBeUndefined();
    expect(wrapper.get('#tracking-stops-panel').attributes('hidden')).toBeDefined();
    expect(wrapper.get('.simulation-summary').text()).toContain('#42 · 51B-42');
    expect(wrapper.find('.simulation-select').exists()).toBe(false);
    expect(wrapper.find('.simulation-fleet-list').exists()).toBe(false);
    expect(wrapper.get('.play-button').text()).toContain('Tạm dừng');
    await wrapper.get('.simulation-locate').trigger('click');
    await stops.trigger('click');
    expect(wrapper.get('#tracking-stops-panel').attributes('hidden')).toBeUndefined();
    expect(wrapper.findAll('.tracking-route-stop')).toHaveLength(2);
    await controls.trigger('click');
    expect(wrapper.get('#main-map').element).toBe(canvas);
    expect(wrapper.attributes('data-workspace')).toBe('tracking');
    expect(onWorkspaceChange).not.toHaveBeenCalled();
    expect(fetchDetail).toHaveBeenCalledTimes(detailCalls);
    expect(fetchRoute).toHaveBeenCalledTimes(routeCalls);
    expect(operations.subscribeOperations).toHaveBeenCalledTimes(1);
    expect(remove).not.toHaveBeenCalled();
    expect(operations.controlSimulation).not.toHaveBeenCalled();
    await wrapper.get('.panel-alert-launcher').trigger('click');
    expect(wrapper.get('.alert-drawer').attributes('hidden')).toBeUndefined();
    if (compact) expect(wrapper.get('.context-drawer').attributes('hidden')).toBeDefined();
    await wrapper.get('button[aria-label="Đóng thông báo vận hành"]').trigger('click');
    expect(wrapper.get('.context-drawer').attributes('hidden')).toBeUndefined();
    expect(controls.attributes('aria-pressed')).toBe('true');
    expect(wrapper.get('#vehicle-controls-panel').attributes('hidden')).toBeUndefined();

    let finishPause!: (value: SimulationRun) => void;
    vi.mocked(operations.controlSimulation).mockReturnValue(new Promise((resolve) => { finishPause = resolve; }));
    await wrapper.get('.play-button').trigger('click');
    expect(operations.controlSimulation).toHaveBeenCalledExactlyOnceWith(42, 'pause', undefined);
    expect(wrapper.get('.simulator-content fieldset').attributes('disabled')).toBeDefined();
    await stops.trigger('click');
    await controls.trigger('click');
    expect(wrapper.get('.simulator-content fieldset').attributes('disabled')).toBeDefined();
    finishPause({ ...run, status: 'PAUSED', updatedAt: '2026-09-23T01:00:01Z' });
    await flushPromises();
    expect(wrapper.get('.play-button').text()).toContain('Tiếp tục');
    expect(operations.controlSimulation).toHaveBeenCalledTimes(1);
    vi.mocked(operations.subscribeOperations).mock.calls[0]![1]();
    await flushPromises();
    expect(wrapper.get('.simulator-content fieldset').attributes('disabled')).toBeDefined();
    expect(wrapper.get('.simulation-connection').text()).toContain('Mất kết nối');
  },
);

test('live monitoring GPS controls remain read-only and changing/clearing the selected trip keeps both views aligned', async () => {
  const first = liveControlsFixture('GPS'), second = liveControlsFixture('SIMULATOR', 43);
  const liveSnapshot = { ...first.liveSnapshot, trips: [first.detail.trip, second.detail.trip],
    positions: [...first.liveSnapshot.positions, ...second.liveSnapshot.positions],
    simulations: second.liveSnapshot.simulations };
  vi.mocked(operations.fetchOperations).mockResolvedValue(liveSnapshot);
  vi.mocked(operations.subscribeOperations).mockImplementation((callback) => {
    callback(liveSnapshot); return vi.fn();
  });
  vi.spyOn(fleetApi, 'fetchTrip').mockImplementation(async id => id === 42 ? first.detail : second.detail);
  vi.spyOn(fleetApi, 'fetchTripRoute').mockResolvedValue(first.detail.route);
  mockLiveControlsEta();
  const wrapper = mount(MapComponent, { props: { initialTripId: 42 },
    global: { stubs: { RouterLink: true } }, attachTo: document.body });
  unmounts.push(() => wrapper.unmount());
  await flushPromises();
  await wrapper.get('.tracking-panel-switch button[aria-controls="vehicle-controls-panel"]').trigger('click');
  expect(wrapper.get('.simulation-summary').text()).toContain('Đang nhận GPS');
  expect(wrapper.get('.simulator-content').text()).toContain('Đang theo dõi GPS trực tiếp');
  expect(wrapper.get('.simulator-content').text()).not.toContain('Sẵn sàng tại trạm đầu');
  expect((wrapper.get('.play-button').element as HTMLButtonElement).disabled).toBe(true);
  expect(operations.controlSimulation).not.toHaveBeenCalled();
  await wrapper.setProps({ initialTripId: 43 });
  await flushPromises();
  expect(wrapper.get('#tracking-stops-panel').attributes('hidden')).toBeUndefined();
  expect(wrapper.get('#vehicle-controls-panel').attributes('hidden')).toBeDefined();
  expect(wrapper.get('.tracking-vehicle-card').text()).toContain('51B-43');
  await wrapper.get('.tracking-panel-switch button[aria-controls="vehicle-controls-panel"]').trigger('click');
  expect(wrapper.get('.simulation-summary').text()).toContain('#43 · 51B-43');
  expect(wrapper.get('.simulation-summary').text()).not.toContain('51B-42');
  await wrapper.get('.tracking-panel-switch button[aria-controls="tracking-stops-panel"]').trigger('click');
  await wrapper.get('.tracking-vehicle-clear').trigger('click');
  await flushPromises();
  expect(wrapper.get('.context-drawer').attributes('hidden')).toBeDefined();
  expect(wrapper.find('.tracking-panel-switch').exists()).toBe(false);
  expect(operations.controlSimulation).not.toHaveBeenCalled();
});

test('map workspace mode changes preserve the Leaflet owner and its SSE subscription', async () => {
  const remove = vi.spyOn(L.Map.prototype, 'remove');
  const wrapper = mount(MapComponent, {
    props: { initialWorkspace: 'tracking', embedded: true },
    attachTo: document.body,
  });
  unmounts.push(() => wrapper.unmount());
  await flushPromises();
  const canvas = wrapper.get('#main-map').element;
  await wrapper.setProps({ initialWorkspace: 'simulation' });
  await flushPromises();
  await wrapper.setProps({ initialWorkspace: 'tracking' });
  await flushPromises();
  expect(wrapper.get('#main-map').element).toBe(canvas);
  expect(wrapper.attributes('data-workspace')).toBe('tracking');
  expect(operations.subscribeOperations).toHaveBeenCalledTimes(1);
  expect(remove).not.toHaveBeenCalled();
  wrapper.unmount();
  expect(remove).toHaveBeenCalledTimes(1);
  expect(vi.mocked(operations.subscribeOperations).mock.results[0].value).toHaveBeenCalledTimes(1);
});
test('station map picker and marker drag resolve addresses and save the latest point', async () => {
  const geocode = vi.spyOn(stationApi, 'reverseGeocodeStation')
    .mockResolvedValueOnce({ address: 'Địa chỉ điểm đầu', distanceMeters: 7 })
    .mockResolvedValueOnce({ address: 'Địa chỉ sau kéo', distanceMeters: 2 });
  const create = vi.spyOn(stationApi, 'createStation').mockImplementation(async (input) => ({
    ...input, id: 1, active: true, createdAt: snapshot.serverTime, updatedAt: snapshot.serverTime,
  }));
  const createMap = vi.spyOn(L, 'map');
  const wrapper = mount(MapComponent, { props: { initialWorkspace: 'stations' }, global: { stubs: { RouterLink: true } }, attachTo: document.body });
  unmounts.push(() => wrapper.unmount());
  await flushPromises();
  await wrapper.get('.add-station-dashed-btn').trigger('click');
  expect(wrapper.get('.context-drawer').classes()).toContain('station-form-open');
  await wrapper.get('.picking-center-btn').trigger('click');
  const address = () => wrapper.get<HTMLInputElement>('.station-form input[maxlength="255"]');
  await vi.waitFor(() => expect(address().element.value).toBe('Địa chỉ điểm đầu'));
  expect(wrapper.find('.address-lookup').exists()).toBe(false);
  expect(geocode).toHaveBeenCalledTimes(1);

  const map: L.Map = createMap.mock.results[0].value;
  let marker: L.Marker | undefined;
  map.eachLayer(layer => {
    if (layer instanceof L.Marker && layer.getElement()?.querySelector('.station-map-marker.draft')) marker = layer;
  });
  expect(marker).toBeDefined();
  marker!.setLatLng([10.9, 106.8]); marker!.fire('dragend');
  await flushPromises();
  expect(address().element.value).toBe('');
  expect(wrapper.get('.address-lookup [role="status"]').text()).toContain('Đang lấy địa chỉ');
  await vi.waitFor(() => expect(address().element.value).toBe('Địa chỉ sau kéo'));
  expect(wrapper.find('.address-lookup').exists()).toBe(false);
  expect(geocode).toHaveBeenLastCalledWith(10.9, 106.8, expect.any(AbortSignal));
  await wrapper.get('.station-form input[maxlength="150"]').setValue('Trạm bản đồ');
  await wrapper.get('.station-form').trigger('submit'); await flushPromises();
  expect(create).toHaveBeenCalledWith({
    name: 'Trạm bản đồ', address: 'Địa chỉ sau kéo', latitude: 10.9, longitude: 106.8, checkinRadiusMeters: 50,
  });
  expect(wrapper.find('.station-form').exists()).toBe(false);
  expect(wrapper.get('.context-drawer').classes()).not.toContain('station-form-open');
  expect(wrapper.get('.station-details').text()).toContain('Địa chỉ sau kéo');
});

test('twenty full map mounts dispose each map and realtime subscription', async () => {
  const remove = vi.spyOn(L.Map.prototype, 'remove');
  for (let index = 0; index < 20; index++) {
    const wrapper = mount(MapComponent, { attachTo: document.body });
    await flushPromises();
    wrapper.unmount();
    await flushPromises();
    expect(remove).toHaveBeenCalledTimes(index + 1);
    expect(
      vi.mocked(operations.subscribeOperations).mock.results[index].value,
    ).toHaveBeenCalledTimes(1);
    expect(document.querySelector('.leaflet-container')).toBeNull();
  }
});

test('changing basemap keeps one tile layer and clears a failed-tile retry and its listeners', async () => {
  const createMap = vi.spyOn(L, 'map'),
    timeout = vi.spyOn(globalThis, 'setTimeout'),
    clear = vi.spyOn(globalThis, 'clearTimeout');
  const wrapper = mount(MapComponent, { attachTo: document.body });
  unmounts.push(() => wrapper.unmount());
  await flushPromises();
  const map: L.Map = createMap.mock.results[0].value;
  const tiles = () => {
    const result: L.TileLayer[] = [];
    map.eachLayer((layer) => {
      if (layer instanceof L.TileLayer) result.push(layer);
    });
    return result;
  };
  expect(tiles()).toHaveLength(1);
  const old = tiles()[0];
  const image = document.createElement('img');
  image.src = 'https://fixture.invalid/tile';
  old.fire('tileerror', { tile: image });
  const retryIndex = timeout.mock.calls.findIndex((call) => call[1] === 600);
  expect(retryIndex).toBeGreaterThanOrEqual(0);
  const timer = timeout.mock.results[retryIndex].value;
  await wrapper.get('.gm-basemap-card:nth-child(3)').trigger('click');
  await flushPromises();
  expect(tiles()).toHaveLength(1);
  expect(tiles()[0]).not.toBe(old);
  expect(tiles()[0].options.className).toBe('dark-map-tiles');
  expect(clear).toHaveBeenCalledWith(timer);
  expect(old.listens('tileerror')).toBe(false);
  expect(old.listens('tileload')).toBe(false);
  expect(() => map.setView([10.9, 106.8], 16, { animate: false })).not.toThrow();
  expect(createMap).toHaveBeenCalledTimes(1);
  expect(operations.subscribeOperations).toHaveBeenCalledTimes(1);
});

test('selecting a simulator vehicle and receiving realtime updates keep the loaded basemap', async () => {
  const trip: OperationsSnapshot['trips'][number] = {
    id: 42,
    vehicleId: 7,
    vehiclePlateNumber: '63B853904',
    vehicleType: 'CAR',
    routeId: 3,
    routeName: 'Bến Thành → Suối Tiên',
    status: 'IN_PROGRESS',
    scheduledDepartureAt: snapshot.serverTime,
    plannedEndAt: snapshot.serverTime,
    startedAt: snapshot.serverTime,
    endedAt: null,
    createdAt: snapshot.serverTime,
    dispatchMode: 'ON_DEMAND',
    scheduleId: null,
    scheduleName: null,
    driver: null,
  };
  const run: SimulationRun = {
    id: 11,
    tripId: trip.id,
    status: 'RUNNING',
    multiplier: 10,
    elapsedSeconds: 60,
    durationSeconds: 900,
    simulatedAt: snapshot.serverTime,
    updatedAt: snapshot.serverTime,
    errorMessage: null,
    replacementTripId: null,
    frame: {
      latitude: 10.77,
      longitude: 106.7,
      heading: 90,
      speedKmh: 22,
      progressPercent: 10,
      nextStopSequence: 2,
      nextStopEtaSeconds: 540,
      dwellRemainingSeconds: 0,
      dwelling: false,
      finished: false,
    },
  };
  const position: OperationsSnapshot['positions'][number] = {
    id: 1,
    eventId: 'simulation-1',
    vehicleId: trip.vehicleId,
    tripId: trip.id,
    recordedAt: snapshot.serverTime,
    receivedAt: snapshot.serverTime,
    simulatedAt: snapshot.serverTime,
    latitude: 10.77,
    longitude: 106.7,
    heading: 90,
    speedKmh: 22,
    accuracyMeters: 1,
    source: 'SIMULATOR',
  };
  const operationSnapshot: OperationsSnapshot = {
    ...snapshot,
    trips: [trip],
    simulations: [run],
    positions: [position],
  };
  let pushOperations: (data: OperationsSnapshot) => void = () => undefined;
  vi.mocked(operations.fetchOperations).mockResolvedValue(operationSnapshot);
  vi.mocked(operations.subscribeOperations).mockImplementation((callback) => {
    pushOperations = callback;
    callback(operationSnapshot);
    return vi.fn();
  });
  const route = { stops: [], sections: [], shapingPoints: [] } as unknown as TripDetail['route'];
  vi.spyOn(fleetApi, 'fetchTrip').mockResolvedValue({ trip, stops: [], route });
  vi.spyOn(fleetApi, 'fetchTripRoute').mockResolvedValue(route);
  vi.spyOn(etaApi, 'fetchTripEta').mockResolvedValue({
    tripId: trip.id,
    routeId: trip.routeId,
    calculatedAt: snapshot.serverTime,
    source: 'ROUTE_SNAPSHOT',
    status: 'AVAILABLE',
    trafficObservedAt: null,
    trafficFetchedAt: null,
    nextStopSequence: 2,
    baselineRemainingSeconds: 840,
    totalRemainingSeconds: 840,
    stops: [],
    affectedSegments: [],
    warning: null,
  });
  const createMap = vi.spyOn(L, 'map');
  const wrapper = mount(MapComponent, {
    props: { initialWorkspace: 'simulation' },
    attachTo: document.body,
    global: { stubs: { RouterLink: true } },
  });
  unmounts.push(() => wrapper.unmount());
  await flushPromises();
  await flushPromises();

  const map: L.Map = createMap.mock.results[0].value;
  const tileLayers = () => {
    const layers: L.TileLayer[] = [];
    map.eachLayer((layer) => {
      if (layer instanceof L.TileLayer) layers.push(layer);
    });
    return layers;
  };
  const basemap = tileLayers()[0];
  const tileContainer = basemap.getContainer();
  const createTiles = vi.spyOn(L, 'tileLayer');
  await wrapper.get('.live-vehicle-marker[data-vehicle-id="7"]').trigger('click');
  await flushPromises();
  expect(wrapper.get('.simulation-summary').text()).toContain('#42 · 63B853904');
  expect(tileLayers()).toHaveLength(1);
  expect(map.hasLayer(basemap)).toBe(true);

  for (let tick = 1; tick <= 3; tick++) {
    const serverTime = new Date(Date.parse(snapshot.serverTime) + tick * 1000).toISOString();
    pushOperations({
      ...operationSnapshot,
      serverTime,
      positions: [
        {
          ...position,
          id: tick + 1,
          eventId: `simulation-${tick + 1}`,
          recordedAt: serverTime,
          longitude: position.longitude + tick * 0.0001,
        },
      ],
      simulations: [
        { ...run, elapsedSeconds: run.elapsedSeconds + tick * 10, updatedAt: serverTime },
      ],
    });
    await flushPromises();
    expect(tileLayers()).toHaveLength(1);
    expect(map.hasLayer(basemap)).toBe(true);
    expect(basemap.getContainer()).toBe(tileContainer);
  }
  expect(createTiles).not.toHaveBeenCalled();
  expect(createMap).toHaveBeenCalledTimes(1);
  expect(operations.subscribeOperations).toHaveBeenCalledTimes(1);
});

test('selecting a scheduled vehicle opens its trip summary without a GPS warning', async () => {
  const trip = {
    id: 42,
    vehicleId: 7,
    vehiclePlateNumber: '63B853904',
    vehicleType: 'CAR' as const,
    routeId: 3,
    routeName: 'Bến Thành → Suối Tiên',
    status: 'SCHEDULED' as const,
    scheduledDepartureAt: snapshot.serverTime,
    plannedEndAt: snapshot.serverTime,
    startedAt: null,
    endedAt: null,
    createdAt: snapshot.serverTime,
    dispatchMode: 'ON_DEMAND' as const,
    scheduleId: null,
    scheduleName: null,
    driver: null,
  };
  const detail = {
    trip,
    stops: [{ sequenceNumber: 1, stationName: 'Bến Thành', latitude: 10.77, longitude: 106.7 }],
    route: {
      totalDistanceMeters: 5000,
      estimatedTripDurationSeconds: 900,
      stops: [
        {
          sequenceNumber: 1,
          role: 'START',
          stationId: 1,
          stationName: 'Bến Thành',
          latitude: 10.77,
          longitude: 106.7,
        },
        {
          sequenceNumber: 2,
          role: 'END',
          stationId: 2,
          stationName: 'Suối Tiên',
          latitude: 10.88,
          longitude: 106.8,
        },
      ],
      sections: [],
      shapingPoints: [],
    },
  } as unknown as TripDetail;
  const plannedSnapshot = { ...snapshot, trips: [trip] };
  vi.mocked(operations.fetchOperations).mockResolvedValue(plannedSnapshot);
  vi.mocked(operations.subscribeOperations).mockImplementation((callback) => {
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
  expect(wrapper.findAll('.tracking-route-stop')).toHaveLength(2);
  expect(wrapper.get('.tracking-route-summary').text()).toContain('Bến Thành');
  expect(wrapper.get('.tracking-route-summary').text()).toContain('Suối Tiên');
  expect(wrapper.get('.tracking-vehicle-card').text()).not.toContain('Xe chưa có vị trí GPS');
  expect((wrapper.get('.tracking-vehicle-start').element as HTMLButtonElement).disabled).toBe(true);
  expect(wrapper.get('.tracking-vehicle-start-note').text()).toContain('phân công tài xế');
  expect(operations.controlSimulation).not.toHaveBeenCalled();
  expect(wrapper.get('.live-follow').attributes('hidden')).toBeDefined();
  await wrapper.get('.tracking-vehicle-clear').trigger('click');
  expect(wrapper.get('.context-drawer').attributes('hidden')).toBeDefined();
});

test.each([false, true])(
  'selected vehicle starts its assigned trip simulation only after the start button is pressed (paused run: %s)',
  async (hasPausedRun) => {
    const trip = {
      id: 42,
      vehicleId: 7,
      vehiclePlateNumber: '63B853904',
      vehicleType: 'CAR' as const,
      routeId: 3,
      routeName: 'Bến Thành → Suối Tiên',
      status: 'SCHEDULED' as const,
      scheduledDepartureAt: snapshot.serverTime,
      plannedEndAt: snapshot.serverTime,
      startedAt: null,
      endedAt: null,
      createdAt: snapshot.serverTime,
      dispatchMode: 'ON_DEMAND' as const,
      scheduleId: null,
      scheduleName: null,
      driver: { id: 9, fullName: 'Nguyễn Văn A', phoneNumber: '0900000000', licenseNumber: 'B2' },
    };
    const operationSnapshot = {
      ...snapshot,
      trips: [trip],
      simulations: hasPausedRun
        ? [
            {
              id: 11,
              tripId: trip.id,
              status: 'PAUSED',
              multiplier: 1,
              elapsedSeconds: 0,
              durationSeconds: 600,
              simulatedAt: snapshot.serverTime,
              updatedAt: snapshot.serverTime,
              errorMessage: null,
              replacementTripId: null,
              frame: null,
            } satisfies SimulationRun,
          ]
        : [],
    };
    vi.mocked(operations.fetchOperations).mockResolvedValue(operationSnapshot);
    vi.mocked(operations.subscribeOperations).mockImplementation((callback) => {
      callback(operationSnapshot);
      return vi.fn();
    });
    vi.spyOn(fleetApi, 'fetchTrip').mockResolvedValue({
      trip,
      stops: [{ sequenceNumber: 1, stationName: 'Bến Thành', latitude: 10.77, longitude: 106.7 }],
      route: { stops: [], sections: [], shapingPoints: [] },
    } as unknown as TripDetail);
    vi.spyOn(fleetApi, 'fetchTripRoute').mockResolvedValue({
      stops: [],
      sections: [],
      shapingPoints: [],
    } as unknown as TripDetail['route']);
    vi.mocked(operations.controlSimulation).mockResolvedValue({
      id: 11,
      tripId: trip.id,
      status: 'RUNNING',
      multiplier: 1,
      elapsedSeconds: 0,
      durationSeconds: 600,
      simulatedAt: snapshot.serverTime,
      updatedAt: snapshot.serverTime,
      errorMessage: null,
      replacementTripId: null,
      frame: null,
    } satisfies SimulationRun);
    const onWorkspaceChange = vi.fn();
    const wrapper = mount(MapComponent, {
      props: { initialTripId: trip.id, onWorkspaceChange },
      attachTo: document.body,
    });
    unmounts.push(() => wrapper.unmount());
    await flushPromises();

    expect((wrapper.get('.tracking-vehicle-start').element as HTMLButtonElement).disabled).toBe(
      false,
    );
    expect(operations.controlSimulation).not.toHaveBeenCalled();
    await wrapper.get('.tracking-vehicle-start').trigger('click');
    await flushPromises();

    expect(operations.controlSimulation).toHaveBeenCalledExactlyOnceWith(
      trip.id,
      'play',
      undefined,
    );
    expect(onWorkspaceChange).not.toHaveBeenCalled();
    expect(wrapper.attributes('data-workspace')).toBe('tracking');
    expect(wrapper.get('.simulation-summary').text()).toContain('#42 · 63B853904');
    expect(wrapper.get('#vehicle-controls-panel').attributes('hidden')).toBeUndefined();
    const [stops, controls] = wrapper.findAll('.tracking-panel-switch button');
    expect(controls.attributes('aria-pressed')).toBe('true');
    await stops.trigger('click');
    expect(wrapper.get('#tracking-stops-panel').attributes('hidden')).toBeUndefined();
    await controls.trigger('click');
    expect(wrapper.get('#vehicle-controls-panel').attributes('hidden')).toBeUndefined();
    expect(operations.controlSimulation).toHaveBeenCalledTimes(1);
    expect(operations.subscribeOperations).toHaveBeenCalledTimes(1);
  },
);

test('replay from live monitoring keeps both views available without entering the simulation workspace or playing automatically', async () => {
  const fixture = liveControlsFixture();
  const completedTrip: TripDetail['trip'] = { ...fixture.detail.trip, status: 'COMPLETED', endedAt: snapshot.serverTime };
  const replayTrip: TripDetail['trip'] = { ...completedTrip, status: 'SCHEDULED', attemptNumber: 2, startedAt: null, endedAt: null };
  const completedRun: SimulationRun = { ...fixture.run, status: 'COMPLETED' };
  const replayRun: SimulationRun = { ...fixture.run, status: 'PAUSED', attemptNumber: 2, elapsedSeconds: 0 };
  const completedSnapshot: OperationsSnapshot = { ...snapshot, trips: [completedTrip], simulations: [completedRun] };
  vi.mocked(operations.fetchOperations).mockResolvedValue(completedSnapshot);
  vi.mocked(operations.subscribeOperations).mockImplementation(callback => { callback(completedSnapshot); return vi.fn(); });
  vi.spyOn(fleetApi, 'fetchTrip')
    .mockResolvedValueOnce({ ...fixture.detail, trip: completedTrip })
    .mockResolvedValue({ ...fixture.detail, trip: replayTrip });
  vi.spyOn(fleetApi, 'fetchTripRoute').mockResolvedValue(fixture.detail.route);
  vi.mocked(operations.controlSimulation).mockResolvedValue(replayRun);
  const onWorkspaceChange = vi.fn();
  const wrapper = mount(MapComponent, { props: { initialTripId: 42, onWorkspaceChange },
    global: { stubs: { RouterLink: true, FleetConfirmDialog: {
      props: ['onConfirm'], template: '<button class="confirm-replay" @click="onConfirm()">Xác nhận</button>',
    } } }, attachTo: document.body });
  unmounts.push(() => wrapper.unmount());
  await flushPromises();
  const canvas = wrapper.get('#main-map').element;
  await wrapper.get('.tracking-vehicle-replay').trigger('click');
  expect(operations.controlSimulation).not.toHaveBeenCalled();
  await wrapper.get('.confirm-replay').trigger('click');
  await flushPromises();
  expect(operations.controlSimulation).toHaveBeenCalledExactlyOnceWith(42, 'reset', undefined);
  expect(wrapper.attributes('data-workspace')).toBe('tracking');
  expect(onWorkspaceChange).not.toHaveBeenCalled();
  expect(wrapper.get('#vehicle-controls-panel').attributes('hidden')).toBeUndefined();
  const [stops, controls] = wrapper.findAll('.tracking-panel-switch button');
  expect(controls.attributes('aria-pressed')).toBe('true');
  await stops.trigger('click');
  expect(wrapper.get('#tracking-stops-panel').attributes('hidden')).toBeUndefined();
  await controls.trigger('click');
  expect(wrapper.get('.simulation-summary').text()).toContain('#42 · 51B-42');
  expect(wrapper.get('.play-button').text()).toContain('Bắt đầu');
  expect(wrapper.get('#main-map').element).toBe(canvas);
  expect(operations.subscribeOperations).toHaveBeenCalledTimes(1);
  expect(operations.controlSimulation).toHaveBeenCalledTimes(1);
});

test.each([false, true])('simulation workspace keeps both views and its trip picker usable after changing vehicles (compact: %s)', async compact => {
  const first = liveControlsFixture(), second = liveControlsFixture('SIMULATOR', 43);
  const liveSnapshot = { ...first.liveSnapshot, trips: [first.detail.trip, second.detail.trip],
    positions: [...first.liveSnapshot.positions, ...second.liveSnapshot.positions],
    simulations: [first.run, second.run] };
  vi.mocked(operations.fetchOperations).mockResolvedValue(liveSnapshot);
  vi.mocked(operations.subscribeOperations).mockImplementation(callback => { callback(liveSnapshot); return vi.fn(); });
  vi.spyOn(fleetApi, 'fetchTrip').mockImplementation(async id => id === 42 ? first.detail : second.detail);
  vi.spyOn(fleetApi, 'fetchTripRoute').mockResolvedValue(first.detail.route);
  mockLiveControlsEta();
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: compact,
    addEventListener: vi.fn(), removeEventListener: vi.fn() })));
  const onWorkspaceChange = vi.fn();
  const wrapper = mount(MapComponent, { props: { initialWorkspace: 'simulation', initialTripId: 42, onWorkspaceChange },
    global: { stubs: { RouterLink: true } }, attachTo: document.body });
  unmounts.push(() => wrapper.unmount());
  await flushPromises();
  const canvas = wrapper.get('#main-map').element;
  const [stops, controls] = wrapper.findAll('.tracking-panel-switch button');
  expect(controls.attributes('aria-pressed')).toBe('true');
  expect(wrapper.get('#vehicle-controls-panel').attributes('hidden')).toBeUndefined();
  expect(wrapper.find('.simulation-select').exists()).toBe(true);
  await stops.trigger('click');
  expect(wrapper.get('.context-drawer').attributes('hidden')).toBeUndefined();
  expect(wrapper.get('#tracking-stops-panel').attributes('hidden')).toBeUndefined();
  expect(wrapper.get('.tracking-vehicle-card').text()).toContain('51B-42');
  await controls.trigger('click');
  expect(onWorkspaceChange).not.toHaveBeenCalled();
  await wrapper.get('select[aria-label="Chọn chuyến mô phỏng"]').setValue('43');
  await flushPromises();
  expect(controls.attributes('aria-pressed')).toBe('true');
  expect(wrapper.get('.simulation-summary').text()).toContain('#43 · 51B-43');
  await stops.trigger('click');
  expect(wrapper.get('.tracking-vehicle-card').text()).toContain('51B-43');
  expect(wrapper.attributes('data-workspace')).toBe('simulation');
  expect(wrapper.get('#main-map').element).toBe(canvas);
  expect(operations.subscribeOperations).toHaveBeenCalledTimes(1);
  expect(operations.controlSimulation).not.toHaveBeenCalled();
});

test('failed basemap tiles expose a toast retry action that replaces the tile layer', async () => {
  const createMap = vi.spyOn(L, 'map');
  const wrapper = mount(MapComponent, { attachTo: document.body });
  unmounts.push(() => wrapper.unmount());
  await flushPromises();
  const map: L.Map = createMap.mock.results[0].value;
  const tiles = () => {
    const result: L.TileLayer[] = [];
    map.eachLayer((layer) => {
      if (layer instanceof L.TileLayer) result.push(layer);
    });
    return result;
  };
  const old = tiles()[0];
  old.fire('loading');
  old.fire('tileerror', { tile: document.createElement('img') });
  old.fire('load');
  await flushPromises();
  expect(notifyError).toHaveBeenCalledWith(
    'Không tải được bản đồ nền. Nhấn thông báo để thử lại.',
    expect.objectContaining({ toastId: 'basemap-error', onClick: expect.any(Function) }),
  );

  const toastCalls = vi.mocked(notifyError).mock.calls;
  const retry = toastCalls[toastCalls.length - 1]?.[1]?.onClick;
  retry?.({} as MouseEvent);
  await flushPromises();
  expect(tiles()).toHaveLength(1);
  expect(tiles()[0]).not.toBe(old);
});

test('trip deep link opens its selected vehicle simulator without starting it on compact screens', async () => {
  const trip = {
    id: 42,
    vehicleId: 7,
    vehiclePlateNumber: '63B853904',
    vehicleType: 'CAR' as const,
    routeId: 3,
    routeName: 'Bến Thành → Suối Tiên',
    status: 'SCHEDULED' as const,
    scheduledDepartureAt: snapshot.serverTime,
    plannedEndAt: snapshot.serverTime,
    startedAt: null,
    endedAt: null,
    createdAt: snapshot.serverTime,
    dispatchMode: 'ON_DEMAND' as const,
    scheduleId: null,
    scheduleName: null,
    driver: null,
  };
  const other = { ...trip, id: 41, vehicleId: 6, vehiclePlateNumber: '63B111111' };
  const operationSnapshot = { ...snapshot, trips: [other, trip] };
  vi.mocked(operations.fetchOperations).mockResolvedValue(operationSnapshot);
  vi.mocked(operations.subscribeOperations).mockImplementation((callback) => {
    callback(operationSnapshot);
    return vi.fn();
  });
  vi.spyOn(fleetApi, 'fetchTrip').mockImplementation(
    async (id) =>
      ({
        trip: id === trip.id ? trip : other,
        stops: [{ sequenceNumber: 1, stationName: 'Bến Thành', latitude: 10.77, longitude: 106.7 }],
        route: { stops: [], sections: [], shapingPoints: [] },
      }) as unknown as TripDetail,
  );
  vi.spyOn(fleetApi, 'fetchTripRoute').mockResolvedValue({
    stops: [],
    sections: [],
    shapingPoints: [],
  } as unknown as TripDetail['route']);
  vi.stubGlobal(
    'matchMedia',
    vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() })),
  );

  const wrapper = mount(MapComponent, {
    props: { initialWorkspace: 'simulation', initialTripId: trip.id },
    attachTo: document.body,
  });
  unmounts.push(() => wrapper.unmount());
  await flushPromises();

  expect(wrapper.get('.context-drawer').attributes('hidden')).toBeUndefined();
  expect(wrapper.get('.simulation-summary').text()).toContain('#42 · 63B853904');
  expect(
    (wrapper.get('select[aria-label="Chọn chuyến mô phỏng"]').element as HTMLSelectElement).value,
  ).toBe('42');
  expect(wrapper.get('.play-button').text()).toContain('Bắt đầu');
  expect(operations.controlSimulation).not.toHaveBeenCalled();
});

test('simulation stop list and route marker open the station information popup', async () => {
  const trip = {
    id: 42,
    attemptNumber: 1,
    vehicleId: 7,
    vehiclePlateNumber: '63B853904',
    vehicleType: 'CAR' as const,
    routeId: 3,
    routeName: 'Bến Thành → Suối Tiên',
    status: 'SCHEDULED' as const,
    scheduledDepartureAt: snapshot.serverTime,
    plannedEndAt: snapshot.serverTime,
    startedAt: null,
    endedAt: null,
    createdAt: snapshot.serverTime,
    dispatchMode: 'ON_DEMAND' as const,
    scheduleId: null,
    scheduleName: null,
    driver: null,
  };
  const tripStops = [
    {
      sequenceNumber: 1,
      stationId: 1,
      stationName: 'Bến Thành',
      latitude: 10.77,
      longitude: 106.7,
      checkinRadiusMeters: 50,
      dwellDurationSeconds: 30,
      arrivalOffsetSeconds: 0,
      departureOffsetSeconds: 30,
      plannedArrivalAt: snapshot.serverTime,
      plannedDepartureAt: snapshot.serverTime,
    },
    {
      sequenceNumber: 2,
      stationId: 2,
      stationName: 'Suối Tiên',
      latitude: 10.88,
      longitude: 106.8,
      checkinRadiusMeters: 75,
      dwellDurationSeconds: 45,
      arrivalOffsetSeconds: 900,
      departureOffsetSeconds: 945,
      plannedArrivalAt: snapshot.serverTime,
      plannedDepartureAt: snapshot.serverTime,
    },
  ];
  const route = {
    id: 3,
    name: trip.routeName,
    transportMode: 'CAR',
    routingProvider: 'HERE',
    totalDistanceMeters: 5000,
    estimatedTravelDurationSeconds: 900,
    baseTravelDurationSeconds: 900,
    totalDwellDurationSeconds: 75,
    estimatedTripDurationSeconds: 975,
    estimatedDepartureAt: snapshot.serverTime,
    calculatedAt: snapshot.serverTime,
    createdAt: snapshot.serverTime,
    stops: tripStops.map((stop, index) => ({
      ...stop,
      role: index === 0 ? 'START' : 'END',
      distanceFromPreviousMeters: index === 0 ? 0 : 5000,
      travelDurationFromPreviousSeconds: index === 0 ? 0 : 900,
    })),
    sections: [],
    shapingPoints: [],
  } as unknown as TripDetail['route'];
  const operationSnapshot: OperationsSnapshot = {
    ...snapshot,
    trips: [trip],
    checkIns: [
      {
        tripId: trip.id,
        revision: 1,
        nextStopSequence: 2,
        awaitingExit: false,
        visits: [
          {
            attemptNumber: 1,
            id: 1,
            tripId: trip.id,
            stopSequence: 1,
            source: 'SIMULATOR',
            evidenceKind: 'ROUTE_TRACE',
            actualArrivalAt: snapshot.serverTime,
            simulatedArrivalAt: snapshot.serverTime,
            detectedAt: snapshot.serverTime,
            fromSampleId: null,
            toSampleId: 1,
            evidenceFraction: 0,
            latitude: 10.77,
            longitude: 106.7,
          },
        ],
      },
    ],
  };
  let pushOperations: (data: OperationsSnapshot) => void = () => undefined;
  vi.mocked(operations.fetchOperations).mockResolvedValue(operationSnapshot);
  vi.mocked(operations.subscribeOperations).mockImplementation((callback) => {
    pushOperations = callback;
    callback(operationSnapshot);
    return vi.fn();
  });
  vi.spyOn(fleetApi, 'fetchTrip').mockResolvedValue({ trip, stops: tripStops, route });
  vi.spyOn(fleetApi, 'fetchTripRoute').mockResolvedValue(route);
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      const body = url.includes('/stations')
        ? [
            {
              id: 1,
              name: 'Bến Thành',
              address: 'Quận 1',
              latitude: 10.77,
              longitude: 106.7,
              checkinRadiusMeters: 50,
              active: true,
              createdAt: snapshot.serverTime,
              updatedAt: snapshot.serverTime,
            },
            {
              id: 2,
              name: 'Suối Tiên',
              address: 'Thành phố Thủ Đức',
              latitude: 10.88,
              longitude: 106.8,
              checkinRadiusMeters: 75,
              active: true,
              createdAt: snapshot.serverTime,
              updatedAt: snapshot.serverTime,
            },
          ]
        : url.includes('/traffic/')
          ? {
              source: 'HERE_LIVE',
              status: 'AVAILABLE',
              observedAt: snapshot.serverTime,
              fetchedAt: snapshot.serverTime,
              ageSeconds: 0,
              warning: null,
              results: [],
            }
          : [];
      return new Response(JSON.stringify(body), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    }),
  );
  const createMap = vi.spyOn(L, 'map');
  const wrapper = mount(MapComponent, {
    props: { initialWorkspace: 'simulation', initialTripId: trip.id },
    attachTo: document.body,
  });
  unmounts.push(() => wrapper.unmount());
  await flushPromises();
  await flushPromises();

  const rows = wrapper.findAll('.simulation-stop-list li');
  expect(rows).toHaveLength(2);
  expect(rows.map((row) => row.attributes('data-state'))).toEqual(['checked-in', 'next']);
  await rows[1].get('button').trigger('click');
  await flushPromises();

  const popup = document.querySelector<HTMLElement>('.simulation-stop-popup');
  expect(popup?.textContent).toContain('Suối Tiên');
  expect(popup?.textContent).toContain('Thành phố Thủ Đức');
  expect(popup?.textContent).toContain('75 m');
  expect(popup?.textContent).toContain('Trạm kế tiếp');

  const map: L.Map = createMap.mock.results[0].value;
  const nextMarker = Array.from(
    (() => {
      const found: L.Marker[] = [];
      map.eachLayer((layer) => {
        if (
          layer instanceof L.Marker &&
          layer.getElement()?.querySelector('.route-stop-map-marker.simulation-next')
        )
          found.push(layer);
      });
      return found;
    })(),
  )[0];
  expect(nextMarker).toBeDefined();
  map.closePopup();

  const checkIns = operationSnapshot.checkIns[0]!;
  pushOperations({
    ...operationSnapshot,
    serverTime: '2026-09-23T01:00:01Z',
    checkIns: [
      {
        ...checkIns,
        revision: 2,
        visits: [
          ...checkIns.visits,
          {
            ...checkIns.visits[0]!,
            id: 2,
            stopSequence: 2,
            latitude: 10.88,
            longitude: 106.8,
          },
        ],
      },
    ],
  });
  await flushPromises();

  expect(wrapper.findAll('.simulation-stop-list li')[1].attributes('data-state')).toBe(
    'checked-in',
  );
  const mountedRouteStops: L.Marker[] = [];
  map.eachLayer((layer) => {
    if (layer instanceof L.Marker && layer.getElement()?.querySelector('.route-stop-map-marker'))
      mountedRouteStops.push(layer);
  });
  expect(mountedRouteStops).toContain(nextMarker);
  expect(
    nextMarker.getElement()?.querySelector('.route-stop-map-marker.simulation-checked-in'),
  ).not.toBeNull();
  nextMarker.fire('click');
  expect(document.querySelector('.simulation-stop-popup')?.textContent).toContain('Suối Tiên');
});
