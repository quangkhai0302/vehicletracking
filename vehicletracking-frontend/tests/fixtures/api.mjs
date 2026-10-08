// Deterministic UI-only data. No requests ever reach an operational backend.
export const stamp = '2026-09-22T01:00:00Z';
export const drivers = ['Nguyễn Văn An', 'Trần Minh Hoàng', 'Lê Thị Thanh', 'Phạm Quốc Bảo'].map(
  (fullName, i) => ({
    id: i + 1,
    fullName,
    phoneNumber: `090123456${i}`,
    licenseNumber: `B2-12000${i}`,
    active: i !== 3,
    createdAt: stamp,
    updatedAt: stamp,
  }),
);
export const vehicles = [
  'Xe tuyến trung tâm',
  'Xe tuyến sân bay',
  'Xe trung chuyển',
  'Xe dự phòng',
].map((name, i) => ({
  id: i + 1,
  name,
  plateNumber: `51B1234${i}`,
  description: 'Phương tiện kiểm thử giao diện',
  vehicleType: 'CAR',
  active: i !== 3,
  driver: i < 2 ? drivers[i] : null,
  createdAt: stamp,
  updatedAt: stamp,
}));
export const trips = Array.from({ length: 12 }, (_, i) => ({
  id: 100 + i,
  attemptNumber: 1,
  vehicleId: (i % 3) + 1,
  vehiclePlateNumber: vehicles[i % 3].plateNumber,
  vehicleType: 'CAR',
  routeId: 1,
  routeName: ['Bến Thành → Suối Tiên', 'Sân bay → Trung tâm', 'Thủ Đức → Quận 1'][i % 3],
  status: ['SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'][i % 4],
  scheduledDepartureAt: stamp,
  plannedEndAt: '2026-09-22T03:00:00Z',
  startedAt: i % 4 ? stamp : null,
  endedAt: null,
  createdAt: stamp,
  dispatchMode: i % 2 ? 'FIXED_SCHEDULE' : 'ON_DEMAND',
  scheduleId: i % 2 ? 1 : null,
  scheduleName: i % 2 ? 'Ca sáng ngày thường' : null,
  driver: drivers[i % 3],
}));
export const routes = [
  {
    id: 1,
    name: 'Bến Thành → Suối Tiên',
    active: true,
    transportMode: 'CAR',
    routingProvider: 'HERE',
    startStationName: 'Bến Thành',
    endStationName: 'Suối Tiên',
    stopCount: 3,
    totalDistanceMeters: 24500,
    estimatedTravelDurationSeconds: 3600,
    totalDwellDurationSeconds: 600,
    estimatedTripDurationSeconds: 4200,
    calculatedAt: stamp,
    createdAt: stamp,
  },
];
export const stations = ['Bến Thành', 'Thảo Điền', 'Suối Tiên'].map((name, i) => ({
  id: i + 1,
  name,
  latitude: 10.77 + i * 0.01,
  longitude: 106.7 + i * 0.01,
  address: 'Địa chỉ kiểm thử',
  checkinRadiusMeters: 100,
  active: true,
  createdAt: stamp,
  updatedAt: stamp,
}));
export const stops = stations.map((station, i) => ({
  sequenceNumber: i + 1,
  role: i === 0 ? 'START' : i === 2 ? 'END' : 'STOP',
  stationId: station.id,
  stationName: station.name,
  latitude: station.latitude,
  longitude: station.longitude,
  checkinRadiusMeters: 100,
  dwellDurationSeconds: 60,
  distanceFromPreviousMeters: i ? 1500 : 0,
  travelDurationFromPreviousSeconds: i ? 600 : 0,
  arrivalOffsetSeconds: i * 600,
  departureOffsetSeconds: i * 600 + 60,
  plannedArrivalAt: stamp,
  plannedDepartureAt: stamp,
}));
export const routeDetail = {
  ...routes[0],
  baseTravelDurationSeconds: 3600,
  estimatedDepartureAt: stamp,
  stops,
  sections: [],
  shapingPoints: [],
};
export const schedules = [true, false, true].map((enabled, i) => ({
  id: i + 1,
  name: ['Ca sáng ngày thường', 'Ca chiều cuối tuần', 'Tuyến sân bay'][i],
  routeId: 1,
  routeName: routes[0].name,
  vehicleId: 1,
  vehiclePlate: vehicles[0].plateNumber,
  driverId: 1,
  driverName: drivers[0].fullName,
  frequency: 'WEEKLY',
  scheduledDate: null,
  weekdaysMask: 31,
  departureTime: '08:00:00',
  timezone: 'Asia/Ho_Chi_Minh',
  effectiveFrom: '2026-09-01',
  effectiveUntil: null,
  enabled,
  nextRunAt: stamp,
  lastRunAt: stamp,
  lastRunStatus: 'SUCCESS',
  lastRunMessage: null,
}));
export const alerts = [0, 1, 2].map((i) => ({
  id: i + 1,
  tripId: 100,
  vehicleId: 1,
  vehiclePlateNumber: vehicles[0].plateNumber,
  revisionId: null,
  type: i ? 'REROUTE_UNAVAILABLE' : 'OFF_ROUTE_DETECTED',
  severity: i ? 'MAJOR' : 'CRITICAL',
  title: i ? 'Tuyến đường cần được kiểm tra' : 'Phương tiện lệch khỏi tuyến được giao',
  reason: 'Cần kiểm tra hành trình và liên hệ tài xế.',
  incidentId: null,
  affectedStopSequences: '',
  baselineEtaSeconds: null,
  revisedEtaSeconds: null,
  createdAt: stamp,
  readAt: i === 2 ? stamp : null,
  measuredDistanceMeters: 450,
  thresholdDistanceMeters: 200,
  breachDurationSeconds: 90,
}));
export const snapshot = {
  serverTime: stamp,
  positions: [
    {
      id: 1,
      eventId: 'fixture-1',
      vehicleId: 2,
      tripId: 101,
      attemptNumber: 1,
      recordedAt: stamp,
      receivedAt: stamp,
      simulatedAt: null,
      latitude: 10.7769,
      longitude: 106.7009,
      speedKmh: 0,
      heading: 90,
      accuracyMeters: 5,
      source: 'GPS',
    },
  ],
  simulations: [],
  trips,
  checkIns: [],
  notifications: alerts,
};

// Report fixtures are simulation attempts, including a frozen archive and an unknown legacy run.
export const simulationAttempts = Array.from({ length: 25 }, (_, index) => {
  const unknown = index === 2;
  const archived = index === 1;
  const late = index % 3 === 0;
  return {
    tripId: 100 + index,
    attemptNumber: archived ? 1 : 2,
    current: !archived,
    vehicleId: unknown ? null : (index % 3) + 1,
    vehiclePlateNumber: unknown ? null : vehicles[index % 3].plateNumber,
    driverId: unknown ? null : (index % 3) + 1,
    driverName: unknown ? null : drivers[index % 3].fullName,
    routeName: unknown ? null : 'Bến Thành → Suối Tiên',
    startedAt: stamp,
    endedAt: index === 0 ? null : '2026-09-22T02:00:00Z',
    status: index === 0 ? 'RUNNING' : 'COMPLETED',
    scenario: unknown ? 'CURRENT_TRAFFIC' : late ? 'CONGESTION' : 'NORMAL',
    plannedDurationSeconds: unknown ? null : 3600,
    plannedDistanceMeters: unknown ? null : 24500,
    virtualElapsedSeconds: unknown ? null : late ? 4800 : 3600,
    progressSeconds: 3600,
    latenessSeconds: unknown ? null : late ? 1200 : 0,
    punctuality: unknown ? 'UNKNOWN' : late ? 'LATE' : 'ON_TIME',
    metadataComplete: !unknown,
    offRouteEventCount: index === 1 ? 2 : 0,
    routeRevisions:
      index === 1
        ? [
            {
              revisionId: 11,
              revisionNumber: 1,
              createdAt: stamp,
              baselineEtaSeconds: 2700,
              revisedEtaSeconds: 1800,
            },
          ]
        : [],
  };
});

export function simulationReportFixture(params = new URLSearchParams(), empty = false) {
  const vehicle = params.get('vehicleId'),
    driver = params.get('driverId');
  const base = (empty ? [] : simulationAttempts).filter(
    (item) =>
      (!vehicle || item.vehicleId === Number(vehicle)) &&
      (!driver || item.driverId === Number(driver)),
  );
  const knownCompleted = base.filter(
    (item) => item.status === 'COMPLETED' && item.metadataComplete,
  );
  const metric = params.get('metric') || 'ALL';
  const filtered = base.filter(
    (item) =>
      metric === 'ALL' ||
      (metric === 'COMPLETED' && item.status === 'COMPLETED') ||
      (metric === 'ON_TIME' && item.punctuality === 'ON_TIME') ||
      (metric === 'LATE' && item.punctuality === 'LATE') ||
      (metric === 'OFF_ROUTE' && item.offRouteEventCount > 0),
  );
  const page = Number(params.get('page') || 0),
    size = Number(params.get('size') || 20);
  return {
    from: params.get('from') || '2026-09-01',
    to: params.get('to') || '2026-09-22',
    generatedAt: stamp,
    attemptCount: base.length,
    completedAttemptCount: base.filter((item) => item.status === 'COMPLETED').length,
    knownCompletedAttemptCount: knownCompleted.length,
    totalPlannedDistanceMeters: base.reduce(
      (sum, item) => sum + (item.plannedDistanceMeters || 0),
      0,
    ),
    totalVirtualSeconds: base.reduce((sum, item) => sum + (item.virtualElapsedSeconds || 0), 0),
    onTimeRatePercent: knownCompleted.length
      ? Math.round(
          (knownCompleted.filter((item) => item.punctuality === 'ON_TIME').length /
            knownCompleted.length) *
            10000,
        ) / 100
      : null,
    lateAttemptCount: base.filter((item) => item.punctuality === 'LATE').length,
    offRouteEventCount: base.reduce((sum, item) => sum + item.offRouteEventCount, 0),
    unknownAttemptCount: base.filter((item) => !item.metadataComplete).length,
    items: filtered.slice(page * size, (page + 1) * size),
    page,
    size,
    totalElements: filtered.length,
    totalPages: Math.ceil(filtered.length / size),
  };
}

export function operationalReportFixture(params = new URLSearchParams(), empty = false) {
  const from = params.get('from') || '2026-09-01';
  const to = params.get('to') || '2026-09-22';
  const summary = {
    from,
    to,
    generatedAt: stamp,
    tripCount: empty ? 0 : 12,
    completedTripCount: empty ? 0 : 8,
    totalDistanceMeters: empty ? 0 : 294000,
    totalRunningSeconds: empty ? 0 : 86400,
    onTimeRatePercent: empty ? 0 : 75,
    lateTripCount: empty ? 0 : 2,
    offRouteEventCount: empty ? 0 : 1,
    overspeedEventCount: empty ? 0 : 1,
    speedLimitKmh: 80,
  };
  return {
    from,
    to,
    generatedAt: stamp,
    summary,
    vehicles: empty ? [] : vehicles.slice(0, 3).map((vehicle, index) => ({
      vehicleId: vehicle.id,
      plateNumber: vehicle.plateNumber,
      vehicleName: vehicle.name,
      tripCount: 4,
      completedTripCount: index === 0 ? 3 : 2,
      lateTripCount: index === 0 ? 1 : 0,
      lateStopCount: index === 0 ? 2 : 0,
      incidentCount: index === 0 ? 1 : 0,
      employeePassengerCount: null,
      trips: Array.from({ length: 4 }, (_, tripIndex) => ({
        tripId: 100 + index * 10 + tripIndex,
        routeName: routes[tripIndex % routes.length].name,
        driverName: drivers[index].fullName,
        scheduledDepartureAt: stamp,
        startedAt: stamp,
        endedAt: tripIndex < (index === 0 ? 3 : 2) ? '2026-09-22T02:00:00Z' : null,
        status: tripIndex < (index === 0 ? 3 : 2) ? 'COMPLETED' : 'IN_PROGRESS',
      })),
    })),
    drivers: empty ? [] : drivers.slice(0, 3).map((driver, index) => ({
      driverId: driver.id,
      driverName: driver.fullName,
      tripCount: 4,
      completedTripCount: index === 0 ? 3 : 2,
      lateTripCount: index === 0 ? 1 : 0,
      lateStopCount: index === 0 ? 2 : 0,
      incidentCount: index === 0 ? 1 : 0,
      employeePassengerCount: null,
      trips: Array.from({ length: 4 }, (_, tripIndex) => ({
        tripId: 100 + index * 10 + tripIndex,
        routeName: routes[tripIndex % routes.length].name,
        vehiclePlateNumber: vehicles[index].plateNumber,
        scheduledDepartureAt: stamp,
        startedAt: stamp,
        endedAt: tripIndex < (index === 0 ? 3 : 2) ? '2026-09-22T02:00:00Z' : null,
        status: tripIndex < (index === 0 ? 3 : 2) ? 'COMPLETED' : 'IN_PROGRESS',
      })),
    })),
    lateStops: empty ? [] : [{
      tripId: 100,
      routeName: routes[0].name,
      vehiclePlateNumber: vehicles[0].plateNumber,
      driverName: drivers[0].fullName,
      stationName: stations[1].name,
      stopSequence: 2,
      plannedArrivalAt: stamp,
      actualArrivalAt: '2026-09-22T01:02:00Z',
      delaySeconds: 120,
    }],
    incidents: empty ? [] : [{ type: 'OFF_ROUTE_DETECTED', severity: 'CRITICAL', count: 1 }, { type: 'OVERSPEED', severity: 'MAJOR', count: 1 }],
    employeePassengerDataAvailable: false,
    employeePassengerDataNote: 'Số người do tài xế xác nhận tại các trạm đón.',
    employeeOccupancy: { completedTripCount: empty ? 0 : 8, tripsWithCompleteBoardingData: empty ? 0 : 7, tripsMissingBoardingData: empty ? 0 : 1, tripsMissingSeatCapacity: empty ? 0 : 1, totalBoardings: empty ? 0 : 35, averageBoardingsPerTrip: empty ? null : 5, averageOnboard: null, seatUtilizationPercent: empty ? null : 50 },
    employeeOccupancyByVehicle: empty ? [] : [{ vehicleId: vehicles[0].id, plateNumber: vehicles[0].plateNumber, vehicleName: vehicles[0].name, seatCapacity: 10, completedTripCount: 3, tripsWithCompleteBoardingData: 3, tripsMissingBoardingData: 0, totalBoardings: 15, averageBoardingsPerTrip: 5, averageOnboard: null, seatUtilizationPercent: 50 }],
    employeeOccupancyByDay: empty ? [] : [{ date: '2026-09-22', completedTripCount: 8, confirmedTripCount: 7, totalBoardings: 35, averageBoardingsPerTrip: 5, seatUtilizationPercent: 50 }],
    employeeOccupancyByStation: empty ? [] : [
      { stationId: stations[0].id, stationName: stations[0].name, visitCount: 7, totalBoardings: 14, averageBoardingsPerVisit: 2 },
      { stationId: stations[1].id, stationName: stations[1].name, visitCount: 7, totalBoardings: 21, averageBoardingsPerVisit: 3 },
    ],
    employeeOccupancyTrips: empty ? [] : Array.from({ length: 8 }, (_, index) => ({
      tripId: 100 + index, routeName: routes[0].name, vehiclePlateNumber: vehicles[index % vehicles.length].plateNumber,
      driverName: drivers[index % drivers.length].fullName, serviceDate: '2026-09-22',
      scheduledDepartureAt: stamp, seatCapacity: 10, complete: index < 7, totalBoardings: index < 7 ? 5 : null,
      pickupStops: [
        { stationId: stations[0].id, stationName: stations[0].name, stopSequence: 1, boardingCount: 2, onboardAfterStop: 2 },
        { stationId: stations[1].id, stationName: stations[1].name, stopSequence: 2, boardingCount: index < 7 ? 3 : null, onboardAfterStop: index < 7 ? 5 : null },
      ],
    })),
    incidentDetails: empty ? [] : [{ id: 'notification-1', tripId: 100, routeName: routes[0].name, vehiclePlateNumber: vehicles[0].plateNumber, driverName: drivers[0].fullName, type: 'OFF_ROUTE_DETECTED', severity: 'CRITICAL', occurredAt: stamp, status: 'RECORDED', detail: 'Xe đi lệch tuyến.' }, { id: 'speed-1', tripId: 100, routeName: routes[0].name, vehiclePlateNumber: vehicles[0].plateNumber, driverName: drivers[0].fullName, type: 'OVERSPEED', severity: 'MAJOR', occurredAt: stamp, status: 'RECORDED', detail: 'Vượt giới hạn tốc độ.' }],
  };
}

export async function installFixture(context, base, state = { role: 'ADMIN', mode: 'data' }) {
  const requests = [];
  await context.addInitScript(
    ({ snapshot }) => {
      const liveResources = { created: 0, closed: 0, active: 0 };
      window.__fixtureLiveResources = liveResources;
      // Named SSE events are controlled; unlike a short HTTP response this never reconnects accidentally.
      class FixtureEventSource extends EventTarget {
        static CONNECTING = 0;
        static OPEN = 1;
        static CLOSED = 2;
        readyState = 1;
        withCredentials;
        url;
        onerror = null;
        constructor(url, options) {
          super();
          this.url = String(url);
          this.withCredentials = options?.withCredentials ?? false;
          liveResources.created++;
          liveResources.active++;
          queueMicrotask(() => {
            if (this.readyState === 1)
              this.dispatchEvent(new MessageEvent('snapshot', { data: JSON.stringify(snapshot) }));
          });
        }
        close() {
          if (this.readyState !== 2) {
            liveResources.closed++;
            liveResources.active--;
          }
          this.readyState = 2;
        }
      }
      window.EventSource = FixtureEventSource;
    },
    { snapshot },
  );
  await context.route('**/*', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    if (url.pathname.includes('/api/v1/')) {
      const path = url.pathname.split('/api/v1')[1];
      requests.push({
        method: request.method(),
        path: path + url.search,
        body: request.postData(),
      });
      const json = (body, status = 200) =>
        route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
      const user = () => ({
        accountId: 1,
        username: 'ui.fixture',
        role: state.role,
        active: true,
        passwordChangeRequired: false,
        driverId: state.role === 'DRIVER' ? 1 : null,
        driverName: state.role === 'DRIVER' ? drivers[0].fullName : null,
      });
      if (path === '/auth/me')
        return state.role === 'GUEST' ? json({ detail: 'Fixture guest' }, 401) : json(user());
      if (path === '/auth/csrf')
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          headers: { 'set-cookie': 'XSRF-TOKEN=fixture-csrf; Path=/; SameSite=Lax' },
          body: '{}',
        });
      if (path === '/auth/register-admin')
        return json({ id: 99, username: 'ui.new', role: 'ADMIN', active: true });
      if (path === '/auth/login') {
        state.role = 'ADMIN';
        return json(user());
      }
      if (path === '/auth/logout') {
        state.role = 'GUEST';
        return route.fulfill({ status: 204 });
      }
      if (state.mode === 'error')
        return json({ detail: 'Dữ liệu kiểm thử: không thể tải, vui lòng thử lại.' }, 503);
      const list = (value) => (state.mode === 'empty' ? [] : value);
      if (path === '/telemetry/snapshot') return json(snapshot);
      if (path === '/reports/simulation')
        return json(simulationReportFixture(url.searchParams, state.mode === 'empty'));
      if (path === '/reports/operations/detail')
        return json(operationalReportFixture(url.searchParams, state.mode === 'empty'));
      if (/^\/trips\/\d+\/simulation\/scenario$/.test(path)) {
        const body = request.postDataJSON();
        const tripId = Number(path.split('/')[2]);
        return json({
          id: tripId,
          tripId,
          attemptNumber: body.attemptNumber,
          status: 'RUNNING',
          multiplier: 1,
          elapsedSeconds: 60,
          durationSeconds: 3600,
          virtualElapsedSeconds: 120,
          scenario: body.scenario,
          simulatedAt: stamp,
          updatedAt: '2026-09-22T01:00:01Z',
          errorMessage: null,
          replacementTripId: null,
          frame: null,
        });
      }
      if (path === '/dashboard/summary')
        return json({
          serverTime: stamp,
          activeVehicleCount: 18,
          activeDriverCount: 16,
          tripsInProgress: 8,
          scheduledTrips: 12,
          completedTrips: 124,
          cancelledTrips: 2,
          overdueTrips: 3,
          offRouteVehicleCount: 1,
          unreadAlertCount: 2,
          pendingAlerts: list(alerts),
        });
      if (path === '/vehicles') return json(list(vehicles));
      if (path === '/drivers') return json(list(drivers));
      if (path === '/trips' || path === '/driver/trips') return json(list(trips));
      if (/^\/(driver\/)?trips\/\d+$/.test(path)) {
        const requestedId = Number(path.split('/').at(-1));
        return json({
          trip: trips.find((t) => t.id === requestedId) ?? trips[0],
          stops,
          route: routeDetail,
        });
      }
      if (path.endsWith('/check-ins'))
        return json({
          tripId: Number(path.split('/').at(-2)),
          revision: 0,
          visits: [],
          nextStopSequence: 1,
          awaitingExit: false,
        });
      if (path.endsWith('/eta'))
        return json({
          tripId: Number(path.split('/').at(-2)),
          status: 'OK',
          source: 'BASE_ROUTE',
          totalRemainingSeconds: 3600,
          stops: [],
        });
      if (path === '/routes') return json(list(routes));
      if (/^\/routes\/\d+$/.test(path)) return json(routeDetail);
      if (path === '/stations') return json(list(stations));
      if (path === '/stations/reverse-geocode')
        return json({ address: 'Địa chỉ gợi ý fixture tại điểm đã chọn', distanceMeters: 12 });
      if (path === '/schedules' || path === '/driver/schedules') return json(list(schedules));
      if (path.startsWith('/notifications')) return json(list(alerts));
      if (path.startsWith('/traffic/'))
        return json({
          source: 'HERE_LIVE',
          status: 'AVAILABLE',
          observedAt: stamp,
          fetchedAt: stamp,
          ageSeconds: 0,
          warning: null,
          results: [],
        });
      if (path === '/users')
        return json(
          list([
            {
              id: 1,
              username: 'quantri',
              role: 'ADMIN',
              active: true,
              driverId: null,
              driverName: null,
              driverLicenseNumber: null,
            },
            {
              id: 2,
              username: 'nguyen.an',
              role: 'DRIVER',
              active: true,
              driverId: 1,
              driverName: drivers[0].fullName,
              driverLicenseNumber: drivers[0].licenseNumber,
            },
          ]),
        );
      if (path === '/reports/operations')
        return json({
          from: '2026-09-01',
          to: '2026-09-22',
          generatedAt: stamp,
          tripCount: state.mode === 'empty' ? 0 : 142,
          completedTripCount: 124,
          totalDistanceMeters: 3456000,
          totalRunningSeconds: 234567,
          onTimeRatePercent: 94.2,
          lateTripCount: 8,
          offRouteEventCount: 4,
          overspeedEventCount: 2,
          speedLimitKmh: 60,
        });
      return json([]);
    }
    if (url.hostname.endsWith('.google.com') && url.pathname.startsWith('/vt')) {
      // Same deterministic provider tile in both apps, not a mask over the map.
      return route.fulfill({
        contentType: 'image/svg+xml',
        body: '<svg xmlns="http://www.w3.org/2000/svg" width="256" height="256"><path fill="#edf0e8" d="M0 0h256v256H0z"/><path fill="none" stroke="#fff" stroke-width="10" d="M0 64h256M128 0v256"/><path fill="none" stroke="#cbd8c6" d="M0 0h256v256H0z"/><path fill="#b6dee7" d="M220 0h18L80 256H62z"/></svg>',
      });
    }
    if (url.origin === new URL(base).origin) return route.continue();
    // Font requests are handled by the capture script's cached font routes.
    return route.abort();
  });
  return requests;
}
