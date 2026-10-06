// Fixture-only checks: every API and external request is intercepted, no trips are modified.
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { chromium } from 'playwright';
import { installFixture, routeDetail, snapshot, stamp } from '../fixtures/api.mjs';
import { encode } from '../unit/fixtures/driverNavigation.ts';

const base = process.env.UI_BASE_URL || 'http://127.0.0.1:5173';
const url = new URL(base);
assert(url.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname),
  'Use a local fixture-only frontend');
const output = process.env.UI_OUTPUT_DIR || '/tmp/vehicletracking-route-comparison';
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true });
const passed = [], errors = [];
let failure = null;

const before = [[10.77, 106.7], [10.77, 106.701], [10.77, 106.703], [10.77, 106.704]];
const firstAfter = [[10.77, 106.7], [10.77, 106.701], [10.771, 106.701],
  [10.771, 106.703], [10.77, 106.703], [10.77, 106.704]];
const secondAfter = [[10.77, 106.7], [10.77, 106.701], [10.769, 106.701],
  [10.769, 106.703], [10.77, 106.703], [10.77, 106.704]];
const comparison = (id) => ({
  tripId: 100, revisionId: id, revisionNumber: id === 11 ? 1 : 2, createdAt: stamp,
  reason: id === 11 ? 'Đã tạo đường thay thế để tránh đoạn ùn tắc.' : 'Tài xế chọn đường khác cho chặng tiếp theo.',
  status: 'AVAILABLE', message: null, attemptNumber: 1,
  anchor: { latitude: 10.77, longitude: 106.7 },
  before: { encodedPolylines: [encode(id === 11 ? before : firstAfter)], distanceMeters: id === 11 ? 2100 : 1400,
    durationSeconds: id === 11 ? 2700 : 1800 },
  after: { encodedPolylines: [encode(id === 11 ? firstAfter : secondAfter)], distanceMeters: id === 11 ? 1400 : 1500,
    durationSeconds: id === 11 ? 1800 : 1700 },
});
const notices = [11, 12].map((id, index) => ({
  id, tripId: 100, vehicleId: 1, vehiclePlateNumber: '51B12340', revisionId: id,
  type: index ? 'DRIVER_ROUTE_CHANGED' : 'REROUTE_CREATED', severity: 'MAJOR',
  title: index ? 'Tài xế đã thay đổi tuyến đường' : 'Đã tạo tuyến đường thay thế',
  reason: comparison(id).reason, incidentId: null, affectedStopSequences: '',
  baselineEtaSeconds: comparison(id).before.durationSeconds,
  revisedEtaSeconds: comparison(id).after.durationSeconds, createdAt: stamp, readAt: stamp,
}));
const liveRoute = { ...routeDetail, sections: [{ sectionSequence: 1, destinationStopSequence: 3,
  encodedPolyline: encode([[10.78, 106.71], [10.785, 106.715]]), distanceMeters: 800,
  travelDurationSeconds: 30, baseTravelDurationSeconds: 30 }] };

try {
  for (const [width, height] of [[1440, 900], [1024, 768], [390, 844]]) {
    const context = await browser.newContext({ viewport: { width, height }, locale: 'vi-VN',
      timezoneId: 'Asia/Ho_Chi_Minh', reducedMotion: 'reduce' });
    let page = null, requests = [];
    try {
      requests = await installFixture(context, base);
      await context.addInitScript(() => {
        window.__comparisonSources = [];
        const ExistingSource = window.EventSource;
        window.EventSource = class extends ExistingSource {
          constructor(...args) { super(...args); window.__comparisonSources.push(this); }
        };
      });
      const record = (route) => requests.push({ method: route.request().method(),
        path: new URL(route.request().url()).pathname.replace('/api/v1', '') });
      await context.route('**/api/v1/notifications**', async route => {
        record(route);
        await route.fulfill({ contentType: 'application/json', body: JSON.stringify(notices) });
      });
      await context.route('**/api/v1/trips/*/route', async route => {
        record(route);
        await route.fulfill({ contentType: 'application/json', body: JSON.stringify(liveRoute) });
      });
      await context.route('**/api/v1/trips/*/revisions/*/comparison', async route => {
        record(route);
        const id = Number(new URL(route.request().url()).pathname.match(/revisions\/(\d+)/)?.[1]);
        const body = id === 13 ? { ...comparison(12), revisionId: 13, status: 'UNAVAILABLE', before: null,
          message: 'Lần đổi tuyến này chưa lưu đủ đường trước thay đổi. Chỉ hiển thị đường sau thay đổi.' }
          : id === 11 || id === 12 ? comparison(id) : { detail: 'Fixture not found' };
        await route.fulfill({ status: id === 777 ? 404 : 200, contentType: 'application/json', body: JSON.stringify(body) });
      });
      page = await context.newPage();
      page.on('pageerror', error => errors.push(error.stack || error.message));
      await page.clock.install({ time: new Date(stamp) });
      await page.goto(`${base}/alerts`);
      const cards = page.locator('.admin-notification-card');
      await cards.first().waitFor();
      assert.equal(await cards.count(), 2);
      assert.equal(await cards.first().getByRole('link', { name: 'Mở giám sát', exact: true }).getAttribute('href'),
        '/operations?tripId=100&revisionId=11');
      assert.equal(await cards.last().getByRole('link', { name: 'Mở giám sát', exact: true }).getAttribute('href'),
        '/operations?tripId=100&revisionId=12');
      await cards.first().getByRole('link', { name: 'Mở giám sát', exact: true }).click();
      const panel = page.getByRole('complementary', { name: 'So sánh đổi tuyến', exact: true });
      await panel.waitFor();
      await page.locator('path.route-comparison-common').first().waitFor({ state: 'attached' });
      await page.clock.runFor(200);
      await page.locator('#main-map').evaluate(element => { element.dataset.fixtureOwner = '057'; });
      assert.equal(new URL(page.url()).search, '?tripId=100&revisionId=11');
      assert.match(await panel.innerText(), /Lần đổi #1/);
      assert.match(await panel.innerText(), /2,1 km/);
      assert.match(await panel.innerText(), /45 phút/);
      assert.equal(await page.locator('path.route-comparison-before').count() > 0, true);
      assert.equal(await page.locator('path.route-comparison-after').count() > 0, true);
      assert.equal(await page.locator('.live-vehicle-marker').count(), 0);
      assert.equal(await page.getByRole('complementary', { name: 'Bảng dữ liệu vận hành' }).isVisible(), false);
      assert.equal(await page.locator('.live-follow').isVisible(), false);
      assert.equal(await page.locator('path[stroke="#0ea5e9"]').count(), 0, 'No current route overlay in history');
      const geometry = await panel.evaluate(element => {
        const box = element.getBoundingClientRect();
        return { left: box.left, right: box.right, top: box.top, bottom: box.bottom,
          viewportWidth: innerWidth, viewportHeight: innerHeight };
      });
      assert(geometry.left >= 0 && geometry.right <= geometry.viewportWidth
        && geometry.top >= 0 && geometry.bottom <= geometry.viewportHeight, `Panel fits at ${width}px`);
      const headingContrast = await panel.locator('h2').evaluate(element => {
        const rgb = getComputedStyle(element).color.match(/[\d.]+/g).slice(0, 3).map(Number);
        const channels = rgb.map(value => value / 255).map(value => value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4);
        const luminance = channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722;
        return 1.05 / (luminance + 0.05);
      });
      assert(headingContrast >= 4.5, `History heading remains readable on the white panel at ${width}px`);
      assert.equal(await page.getByRole('region', { name: 'Thông tin giao thông', exact: true }).isVisible(), false,
        'Live traffic controls must not obscure or mislabel a historical comparison');
      const overlap = await page.locator('.gm-control-stack').evaluate(element => {
        if (!element.checkVisibility()) return false;
        const controls = element.getBoundingClientRect();
        const sheet = document.querySelector('.route-comparison-panel').getBoundingClientRect();
        return Math.min(controls.right, sheet.right) > Math.max(controls.left, sheet.left)
          && Math.min(controls.bottom, sheet.bottom) > Math.max(controls.top, sheet.top);
      });
      assert.equal(overlap, false, `Map controls cannot obscure the history panel at ${width}px`);
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
      await page.screenshot({ path: `${output}/comparison-${width}.png` });
      const resources = await page.evaluate(() => ({ ...window.__fixtureLiveResources }));
      const fingerprint = () => page.locator('.leaflet-routeComparison-pane').evaluate(element => ({
        paths: Array.from(element.querySelectorAll('path')).map(path => ({ d: path.getAttribute('d'), class: path.getAttribute('class') })),
        transform: element.closest('.leaflet-map-pane').style.transform,
      }));
      const historical = await fingerprint();
      assert.equal(await page.evaluate(() => window.__comparisonSources.filter(source => source.readyState === 1).length) > 0, true,
        'Controlled SSE source is connected before injecting the newer replay snapshot');
      const updated = { ...snapshot, serverTime: '2026-09-22T01:01:00Z', notifications: notices,
        trips: snapshot.trips.map(trip => trip.id === 100 ? { ...trip, status: 'IN_PROGRESS', attemptNumber: 2 } : trip),
        positions: [{ ...snapshot.positions[0], vehicleId: 1, tripId: 100, attemptNumber: 2,
          latitude: 10.785, longitude: 106.715, recordedAt: '2026-09-22T01:01:00Z', receivedAt: '2026-09-22T01:01:00Z' }],
      };
      await page.evaluate(value => {
        for (const source of window.__comparisonSources) if (source.readyState === 1)
          source.dispatchEvent(new MessageEvent('snapshot', { data: JSON.stringify(value) }));
      }, updated);
      await page.clock.runFor(500);
      assert.deepEqual(await fingerprint(), historical, 'SSE replay/latest location cannot change historical geometry or camera');
      assert.deepEqual(await page.evaluate(() => ({ ...window.__fixtureLiveResources })), resources);
      assert.equal(requests.filter(request => request.path.endsWith('/comparison')).length, 1,
        'SSE must not refetch immutable comparison data');

      await panel.getByRole('button', { name: 'Trước thay đổi', exact: true }).click();
      assert.equal(await panel.getByRole('button', { name: 'Trước thay đổi', exact: true }).getAttribute('aria-pressed'), 'true');
      assert.equal(await page.locator('path.route-comparison-after').count(), 0);
      assert.equal(await page.locator('path.route-comparison-common').count(), 0);
      assert.equal(await page.locator('path.route-comparison-before').count() > 0, true);
      await page.screenshot({ path: `${output}/before-${width}.png` });
      await panel.getByRole('button', { name: 'Sau thay đổi', exact: true }).click();
      assert.equal(await page.locator('path.route-comparison-before').count(), 0);
      assert.equal(await page.locator('path.route-comparison-after').count() > 0, true);
      await page.screenshot({ path: `${output}/after-${width}.png` });
      await panel.getByRole('button', { name: 'So sánh', exact: true }).click();
      await panel.getByRole('button', { name: 'Xem vùng thay đổi', exact: true }).click();
      await panel.getByRole('button', { name: 'Về giám sát trực tiếp', exact: true }).click();
      await panel.waitFor({ state: 'detached' });
      assert.equal(new URL(page.url()).search, '?tripId=100');
      assert.equal(await page.locator('.route-comparison-path').count(), 0);
      assert.equal(await page.locator('.route-comparison-anchor').count(), 0);
      assert.equal(await page.locator('#main-map').getAttribute('data-fixture-owner'), '057');
      assert.deepEqual(await page.evaluate(() => ({ ...window.__fixtureLiveResources })), resources);
      passed.push(`Exact notification revision, three modes, historical SSE/replay isolation, responsive panel and exit cleanup at ${width}px`);
      console.log(`PASS ${passed.at(-1)}`);

      await page.goto(`${base}/alerts`);
      await cards.last().waitFor();
      await cards.last().getByRole('link', { name: 'Mở giám sát', exact: true }).click();
      await panel.waitFor();
      await page.locator('path.route-comparison-common').first().waitFor({ state: 'attached' });
      assert.match(await panel.innerText(), /Lần đổi #2/);
      assert.match(await panel.innerText(), /1,5 km/);
      await page.screenshot({ path: `${output}/second-change-${width}.png` });
      const second = await fingerprint();
      assert.notDeepEqual(second.paths, historical.paths, 'Each event has a distinct saved before/after pair');
      await page.goto(`${base}/operations?tripId=100&revisionId=11`);
      await page.locator('path.route-comparison-common').first().waitFor({ state: 'attached' });
      await page.clock.runFor(200);
      assert.match(await panel.innerText(), /Lần đổi #1/);
      assert.match(await panel.innerText(), /2,1 km/);
      assert.deepEqual((await fingerprint()).paths, historical.paths, 'The old event restores the same geometry after viewing a newer revision');
      passed.push(`Second revision differs and the old notification still shows the first snapshot at ${width}px`);
      console.log(`PASS ${passed.at(-1)}`);

      if (width === 1440) {
        await page.goto(`${base}/operations?tripId=100&revisionId=13`);
        await page.locator('path.route-comparison-after').first().waitFor({ state: 'attached' });
        assert.match(await panel.innerText(), /Chỉ hiển thị đường sau thay đổi/);
        assert.equal(await panel.getByRole('button', { name: 'So sánh', exact: true }).isDisabled(), true);
        assert.equal(await panel.getByRole('button', { name: 'Trước thay đổi', exact: true }).isDisabled(), true);
        assert.equal(await page.locator('path.route-comparison-before').count(), 0);
        await page.screenshot({ path: `${output}/legacy-after-only.png` });
        await page.goto(`${base}/operations?tripId=100&revisionId=777`);
        await panel.getByRole('alert').waitFor();
        assert.match(await panel.innerText(), /Không tìm thấy lần đổi tuyến/);
        assert.equal(await page.locator('.route-comparison-path').count(), 0);
        await panel.getByRole('button', { name: 'Thử lại', exact: true }).click();
        await panel.getByRole('alert').waitFor();
        passed.push('Legacy after-only and missing historical revision remain explicit with retry');
        console.log(`PASS ${passed.at(-1)}`);
      }
      assert.equal(requests.some(request => request.method !== 'GET' && !request.path.startsWith('/auth/')), false,
        'Historical viewing must not modify operational resources');
    } catch (error) {
      if (page) {
        await page.screenshot({ path: `${output}/failed-${width}.png` }).catch(() => {});
        await writeFile(`${output}/failed-${width}.json`, JSON.stringify({
          url: page.url(), text: await page.locator('body').innerText().catch(() => ''), requests,
        }, null, 2));
      }
      throw error;
    } finally { await context.close(); }
  }
  assert.deepEqual(errors, [], 'No browser runtime errors');
} catch (error) {
  failure = error instanceof Error ? error.message : String(error);
  throw error;
} finally {
  await writeFile(`${output}/results.json`, JSON.stringify({ fixtureOnly: true, passed, errors, failure }, null, 2));
  await browser.close();
}
