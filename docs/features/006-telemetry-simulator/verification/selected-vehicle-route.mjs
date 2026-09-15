// Isolated browser fixtures: no real backend, HERE requests or database writes.
import assert from 'node:assert/strict';
import { chromium } from '../../004-operations-layout/verification/node_modules/playwright/index.mjs';
import { startFixtureServer } from './fixture-server.mjs';

const fixture = await startFixtureServer();
const template = await fetch(`${fixture.api}/trips/1`).then(response => response.json());
await fixture.close();
const stamp = new Date().toISOString();
const details = [1, 2, 3, 4].map(id => ({
  ...structuredClone(template),
  trip: { ...template.trip, id, vehicleId: id, vehiclePlateNumber: `TEST-${id}`, vehicleType: id === 2 ? 'MOTORCYCLE' : 'CAR', routeId: id,
    status: id === 3 ? 'SCHEDULED' : 'IN_PROGRESS' },
  route: { ...template.route, id, name: `TEST route ${id}` },
}));
const positions = [1, 2, 4].map(id => ({ id, eventId: `test-${id}`, vehicleId: id, tripId: id,
  recordedAt: stamp, receivedAt: stamp, simulatedAt: stamp, latitude: 10.77 + id * .002,
  longitude: 106.7 + id * .002, speedKmh: 20, heading: 45, accuracyMeters: 0, source: id === 4 ? 'GPS' : 'SIMULATOR' }));
const snapshot = { serverTime: stamp, positions, trips: details.map(item => item.trip), notifications: [],
  checkIns: [], simulations: [1, 2].map(id => ({ id, tripId: id, status: 'PAUSED', multiplier: 1,
    elapsedSeconds: 1, durationSeconds: 44, simulatedAt: stamp, updatedAt: stamp, frame: null,
    errorMessage: null, replacementTripId: null })) };
const browser = await chromium.launch({ executablePath: process.env.VERIFICATION_BROWSER_PATH || '/usr/bin/google-chrome', headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
const errors = [];
page.on('pageerror', error => errors.push(error.message));
await page.addInitScript(() => {
  window.EventSource = class extends EventTarget {
    constructor() {
      super();
      this.receive = event => this.dispatchEvent(new MessageEvent('snapshot', { data: JSON.stringify(event.detail) }));
      window.addEventListener('fixture-snapshot', this.receive);
    }
    close() { window.removeEventListener('fixture-snapshot', this.receive); }
  };
});
let delayTrip2 = false;
const writes = [];
const historyRequests = [];
await page.route('**/*', async route => {
  const url = new URL(route.request().url());
  const path = url.pathname;
  if (path.endsWith('/telemetry/history')) historyRequests.push(path);
  if (path.startsWith('/api/') && route.request().method() !== 'GET') writes.push(path);
  if (!path.startsWith('/api/')) {
    return url.hostname === '127.0.0.1' ? route.continue() : route.abort();
  }
  const json = body => route.fulfill({ json: body });
  if (path.endsWith('/telemetry/snapshot')) return json(snapshot);
  if (path.endsWith('/telemetry/stream')) return route.fulfill({ status: 503, body: '' });
  if (path.endsWith('/stations')) return json([]);
  if (path.endsWith('/vehicles')) return json(details.map(({ trip }) => ({ id: trip.vehicleId,
    plateNumber: trip.vehiclePlateNumber, vehicleType: trip.vehicleType, name: 'TEST vehicle', active: true, createdAt: stamp, updatedAt: stamp })));
  if (path.endsWith('/trips')) return json(snapshot.trips);
  if (path.endsWith('/routes')) return json(details.map(item => ({ ...item.route, stopCount: 3, startStationName: 'A', endStationName: 'A' })));
  const match = path.match(/\/(trips|routes)\/(\d+)$/);
  if (match) {
    if (match[1] === 'trips' && match[2] === '2' && delayTrip2) await new Promise(resolve => setTimeout(resolve, 800));
    const detail = details.find(item => item.trip.id === Number(match[2]));
    return json(match[1] === 'routes' ? detail.route : detail);
  }
  if (path.endsWith('/check-ins')) return json({ tripId: Number(path.split('/')[4]), revision: 0, visits: [], nextStopSequence: 1, awaitingExit: false });
  if (path.endsWith('/revisions')) return json([]);
  return route.fulfill({ status: 503, json: { detail: 'Intentionally unavailable in route visibility test' } });
});
async function count(selector, expected) {
  await page.waitForFunction(({ selector, expected }) => document.querySelectorAll(selector).length === expected,
    { selector, expected });
}
const marker = id => page.locator(`.live-vehicle-marker[data-vehicle-id="${id}"]`);
const paths = '.simulation-route-path';
const stopMarkers = '.route-stop-map-marker';
async function panels(id) {
  await page.getByRole('region', { name: 'Chi tiết chuyến đi', exact: true })
    .getByRole('heading', { name: `Chuyến #${id}`, exact: true }).waitFor();
  await page.waitForFunction(id => document.querySelector('.simulation-summary strong')?.textContent === `#${id} · TEST-${id}`, id);
  assert.equal(await page.getByLabel('Chọn chuyến mô phỏng', { exact: true }).inputValue(), String(id));
}
try {
  await page.goto(process.env.VERIFICATION_UI || 'http://127.0.0.1:5178');
  await count('.live-vehicle-marker', 3);
  await count(stopMarkers, 0);
  await marker(1).dispatchEvent('click');
  await count(stopMarkers, 3);
  await count('.route-inspection-hit', 2);
  await panels(1);
  await count('[aria-label="Lịch sử vị trí"]', 0);
  await page.getByRole('region', { name: 'Chi tiết chuyến đi', exact: true })
    .getByText('Chưa có dữ liệu check-in cho chuyến này.', { exact: true }).waitFor();
  snapshot.checkIns = [{ tripId: 1, revision: 1, nextStopSequence: 2, awaitingExit: false,
    visits: [{ id: 1, tripId: 1, stopSequence: 1, source: 'SIMULATOR', evidenceKind: 'POINT',
      actualArrivalAt: stamp, simulatedArrivalAt: stamp, detectedAt: stamp, fromSampleId: null,
      toSampleId: 1, evidenceFraction: 0, latitude: 10.77, longitude: 106.7 }] }];
  snapshot.serverTime = new Date().toISOString();
  await page.evaluate(snapshot => window.dispatchEvent(new CustomEvent('fixture-snapshot', { detail: snapshot })), snapshot);
  await count('.trip-timeline .trip-checkin-done', 1);
  await page.getByRole('region', { name: 'Chi tiết chuyến đi', exact: true })
    .getByText('Đã ghi nhận 1/3 điểm dừng.', { exact: true }).waitFor();
  assert.match(await page.locator('.trip-timeline .trip-checkin-done').innerText(), /Đã qua trạm lúc.*Mô phỏng/);
  await count('.gm-coord-badge', 0);
  await count('.simulator-content .trip-traffic-card', 0);
  await count('.simulator-content .trip-checkin-summary', 0);
  await count('[aria-label="Thay đổi tuyến đường"]', 0);
  assert.equal(await page.getByTestId('simulation-elapsed').isVisible(), false);
  assert.equal(await page.locator('.trip-traffic-position').isVisible(), false);
  console.log('PASS trip UI hides telemetry history and updates check-in count/time from realtime snapshot');
  delayTrip2 = true;
  await marker(2).dispatchEvent('click');
  await count(stopMarkers, 0);
  await count('.route-inspection-hit', 0);
  await count(stopMarkers, 3);
  await panels(2);
  await page.getByRole('button', { name: 'Bỏ chọn xe', exact: true }).click();
  await count(stopMarkers, 0);
  await count('.route-inspection-hit', 0);
  assert.equal(await page.getByLabel('Chọn chuyến mô phỏng', { exact: true }).inputValue(), '');
  await count('[aria-label="Chi tiết chuyến đi"]', 0);
  console.log('PASS tracking: selection, switch while loading, deselection and hit-layer cleanup');

  await marker(2).dispatchEvent('click');
  await page.getByRole('button', { name: 'Bỏ chọn xe', exact: true }).click();
  await page.waitForTimeout(1000);
  await count(stopMarkers, 0);
  await count('[aria-label="Chi tiết chuyến đi"]', 0);
  await count('.simulation-summary', 0);
  delayTrip2 = false;
  console.log('PASS late response cannot restore a deselected route');

  await page.getByRole('button', { name: 'Mô phỏng', exact: true }).first().click();
  await count('.simulation-waiting-marker', 1);
  await count(paths, 0);
  await marker(1).dispatchEvent('click');
  await count(paths, 1);
  assert.equal(await page.locator(paths).getAttribute('data-simulation-route-trip'), '1');
  await panels(1);
  await marker(2).dispatchEvent('click');
  await count('[data-simulation-route-trip="2"]', 1);
  await count('[data-simulation-route-trip="1"]', 0);
  await panels(2);
  await page.getByLabel('Lớp bản đồ', { exact: true }).click();
  await page.getByLabel('Tuyến & điểm nháp', { exact: true }).uncheck();
  await count(paths, 0);
  await page.getByLabel('Tuyến & điểm nháp', { exact: true }).check();
  await count('[data-simulation-route-trip="2"]', 1);
  await count(paths, 1);
  await page.getByLabel('Lớp bản đồ', { exact: true }).click();
  await page.getByRole('button', { name: 'Bỏ chọn xe', exact: true }).click();
  await count(paths, 0);
  await count(stopMarkers, 0);
  await count('.live-vehicle-marker', 3);
  console.log('PASS simulation: no selection, A → B, deselection; both vehicle markers remain');

  await page.locator('.simulation-waiting-marker').dispatchEvent('click');
  await count('[data-simulation-route-trip="3"]', 1);
  await count(paths, 1);
  // Compact layout can show one panel at a time; both must still own the same trip.
  await page.getByRole('button', { name: 'Đội xe', exact: true }).last().click();
  await panels(3);
  await page.getByRole('button', { name: 'Bỏ chọn xe', exact: true }).click();
  await count(paths, 0);
  console.log('PASS waiting vehicle: select and deselect route before telemetry exists');

  await page.getByRole('button', { name: 'Tuyến & trạm', exact: true }).first().click();
  await page.locator('.route-card').filter({ hasText: 'TEST route 1' }).click();
  await count(stopMarkers, 3);
  await count('.route-inspection-hit', 2);
  await page.getByRole('button', { name: 'Theo dõi', exact: true }).first().click();
  await count(stopMarkers, 0);
  await count('.route-inspection-hit', 0);
  console.log('PASS route management preview preserved; no route leaks back into tracking');
  await marker(4).dispatchEvent('click');
  await panels(4);
  await page.getByRole('button', { name: 'Đóng chi tiết chuyến', exact: true }).click();
  await count('[aria-label="Chi tiết chuyến đi"]', 0);
  await marker(4).dispatchEvent('click');
  await panels(4);
  assert.deepEqual(writes, []);
  assert.deepEqual(historyRequests, []);
  console.log('PASS both panels follow running/waiting/GPS vehicle selection; reselect reopens detail; no mutation requests');
  assert.deepEqual(errors, []);
} finally {
  await browser.close();
}
