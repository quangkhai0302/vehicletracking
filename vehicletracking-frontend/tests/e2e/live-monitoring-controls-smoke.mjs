// Fixture-only browser checks: no operational backend or trips are changed.
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { chromium } from 'playwright';
import { installFixture, routeDetail, stamp } from '../fixtures/api.mjs';

const base = process.env.UI_BASE_URL || 'http://127.0.0.1:5173';
const url = new URL(base);
assert(url.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname),
  'Use a local fixture-only frontend');
const output = process.env.UI_OUTPUT_DIR || '/tmp/vehicletracking-054-browser';
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true });
const passed = [], errors = [];
let failure = null;

try {
  for (const [width, height] of [[1440, 1000], [390, 844], [320, 740]]) {
    const context = await browser.newContext({ viewport: { width, height }, locale: 'vi-VN',
      timezoneId: 'Asia/Ho_Chi_Minh', reducedMotion: 'reduce' });
    try {
      const requests = await installFixture(context, base);
      await context.route('**/api/v1/trips/*/route', async route => {
        requests.push({ method: route.request().method(), path: new URL(route.request().url()).pathname.replace('/api/v1', '') });
        await route.fulfill({ contentType: 'application/json', body: JSON.stringify(routeDetail) });
      });
      const page = await context.newPage();
      page.on('pageerror', error => errors.push(error.stack || error.message));
      await page.clock.install({ time: new Date(stamp) });
      await page.goto(`${base}/operations`);
      await page.locator('#main-map.leaflet-container').waitFor();
      await page.locator('#main-map').evaluate(element => { element.dataset.fixtureOwner = '054'; });
      await page.locator('.live-vehicle-marker[data-vehicle-id="2"]').click();
      const drawer = page.getByRole('complementary', { name: 'Bảng dữ liệu vận hành' });
      await drawer.waitFor();
      const stops = drawer.getByRole('button', { name: 'Danh sách trạm', exact: true });
      const controls = drawer.getByRole('button', { name: 'Bảng điều khiển', exact: true });
      assert.equal(await stops.getAttribute('aria-pressed'), 'true');
      assert.equal(await page.locator('#tracking-stops-panel').isVisible(), true);
      const tripRequests = () => requests.filter(request => /^\/trips\/\d+(\/route)?$/.test(request.path)).length;
      const detailRequests = tripRequests();
      const resources = await page.evaluate(() => ({ ...window.__fixtureLiveResources }));
      assert.equal(resources.active, 1);

      // Keyboard activation follows the same buttons as mouse/touch activation.
      await controls.focus();
      await page.keyboard.press('Enter');
      await page.locator('#vehicle-controls-panel').waitFor();
      assert.equal(await controls.getAttribute('aria-pressed'), 'true');
      assert.equal(await page.locator('#tracking-stops-panel').isVisible(), false);
      assert.match(await drawer.locator('.simulation-summary').innerText(), /#101 · 51B12341/);
      assert.match(await drawer.locator('.simulation-summary').innerText(), /Đang nhận GPS/);
      assert.match(await drawer.locator('.simulator-content').innerText(), /Đang theo dõi GPS trực tiếp/);
      assert.doesNotMatch(await drawer.locator('.simulator-content').innerText(), /Sẵn sàng tại trạm đầu/);
      assert.equal(await drawer.locator('.play-button').isDisabled(), true);
      assert.equal(await drawer.locator('.simulation-select').count(), 0);
      assert.equal(await drawer.locator('.simulation-fleet-list').count(), 0);
      const boxes = await drawer.locator('.tracking-panel-switch button').evaluateAll(buttons => buttons.map(button => {
        const rect = button.getBoundingClientRect();
        return { left: rect.left, right: rect.right, height: rect.height,
          contentFits: button.scrollWidth <= button.clientWidth };
      }));
      assert.equal(boxes.length, 2);
      for (const box of boxes) {
        assert(box.left >= 0 && box.right <= width && box.height >= 40 && box.contentFits,
          `Panel switch must fit at ${width}px`);
      }
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
      await page.screenshot({ path: `${output}/controls-${width}.png` });

      await drawer.getByRole('button', { name: 'Định vị xe và toàn tuyến', exact: true }).click();
      assert.equal(new URL(page.url()).pathname + new URL(page.url()).search, '/operations');
      await stops.click();
      await page.locator('#tracking-stops-panel').waitFor();
      assert.equal(await controls.getAttribute('aria-pressed'), 'false');
      assert.equal(await drawer.locator('.tracking-route-stop').count(), 3);
      await page.screenshot({ path: `${output}/stops-${width}.png` });
      await controls.click();
      assert.equal(await page.locator('#main-map').getAttribute('data-fixture-owner'), '054');
      assert.deepEqual(await page.evaluate(() => ({ ...window.__fixtureLiveResources })), resources);
      assert.equal(tripRequests(), detailRequests, 'Switching views must not reload trip/route');
      assert.equal(requests.some(request => request.path.includes('/simulation/') && request.method === 'POST'), false);
      if (width < 900) assert.equal(await page.locator('.live-follow').isVisible(), false);
      await drawer.getByRole('button', { name: 'Thu bảng dữ liệu', exact: true }).click();
      await drawer.waitFor({ state: 'hidden' });
      await page.getByRole('button', { name: 'Bỏ chọn xe', exact: true }).click();
      await drawer.waitFor({ state: 'hidden' });
      assert.equal(await page.locator('.tracking-panel-switch').count(), 0);
      passed.push(`Live monitoring stops/controls, GPS, keyboard and SSE at ${width}px`);
      console.log(`PASS ${passed.at(-1)}`);

      // The start action used to navigate to simulation and hide both switch buttons.
      const simulationRequests = [];
      await context.route('**/api/v1/trips/100/simulation/*', async route => {
        const action = new URL(route.request().url()).pathname.split('/').at(-1);
        simulationRequests.push(action);
        await route.fulfill({ contentType: 'application/json', body: JSON.stringify({
          id: 100, tripId: 100, attemptNumber: 1, status: action === 'pause' ? 'PAUSED' : 'RUNNING',
          multiplier: 1, elapsedSeconds: 10, durationSeconds: 4200,
          simulatedAt: stamp, updatedAt: stamp, errorMessage: null, replacementTripId: null, frame: null,
        }) });
      });
      await page.goto(`${base}/operations?tripId=100`);
      await drawer.waitFor();
      const start = drawer.getByRole('button', { name: 'Bắt đầu mô phỏng', exact: true });
      await start.click();
      await page.locator('#vehicle-controls-panel').waitFor();
      await drawer.getByRole('button', { name: 'Tạm dừng mô phỏng', exact: true }).waitFor();
      assert.equal(new URL(page.url()).search, '?tripId=100');
      assert.equal(await page.locator('.map-first').getAttribute('data-workspace'), 'tracking');
      assert.equal(await controls.getAttribute('aria-pressed'), 'true');
      assert.deepEqual(simulationRequests, ['play']);
      const startResources = await page.evaluate(() => ({ ...window.__fixtureLiveResources }));
      await page.locator('#main-map').evaluate(element => { element.dataset.fixtureOwner = 'started-054'; });
      await stops.click();
      await page.locator('#tracking-stops-panel').waitFor();
      await controls.click();
      await page.locator('#vehicle-controls-panel').waitFor();
      assert.equal(await page.locator('#main-map').getAttribute('data-fixture-owner'), 'started-054');
      assert.deepEqual(await page.evaluate(() => ({ ...window.__fixtureLiveResources })), startResources);
      assert.deepEqual(simulationRequests, ['play']);
      await page.screenshot({ path: `${output}/started-controls-${width}.png` });
      passed.push(`Start simulation in live monitoring preserves workspace and switch buttons at ${width}px`);
      console.log(`PASS ${passed.at(-1)}`);

      // Existing links can still open the dedicated simulation workspace.
      await page.goto(`${base}/operations?mode=simulation&tripId=100`);
      await drawer.waitFor();
      await page.locator('#vehicle-controls-panel').waitFor();
      await drawer.getByRole('button', { name: 'Bắt đầu mô phỏng', exact: true }).click();
      await drawer.getByRole('button', { name: 'Tạm dừng mô phỏng', exact: true }).waitFor();
      assert.deepEqual(simulationRequests, ['play', 'play']);
      await stops.click();
      await page.locator('#tracking-stops-panel').waitFor();
      assert.match(await drawer.locator('.tracking-vehicle-card').innerText(), /51B12340/);
      await controls.click();
      await page.locator('#vehicle-controls-panel').waitFor();
      assert.equal(await drawer.locator('.simulation-select').count(), 1);
      await drawer.getByRole('combobox', { name: 'Chọn chuyến mô phỏng', exact: true }).selectOption('101');
      assert.match(await drawer.locator('.simulation-summary').innerText(), /#101 · 51B12341/);
      assert.equal(await controls.getAttribute('aria-pressed'), 'true');
      await stops.click();
      await page.locator('#tracking-stops-panel').waitFor();
      assert.match(await drawer.locator('.tracking-vehicle-card').innerText(), /51B12341/);
      assert.equal(await page.locator('.map-first').getAttribute('data-workspace'), 'simulation');
      assert.deepEqual(simulationRequests, ['play', 'play']);
      await page.screenshot({ path: `${output}/simulation-stops-${width}.png` });
      passed.push(`Dedicated simulation retains switch buttons and vehicle picker at ${width}px`);
      console.log(`PASS ${passed.at(-1)}`);
    } finally {
      await context.close();
    }
  }
  assert.deepEqual(errors, [], 'No browser runtime errors');
} catch (error) {
  failure = error instanceof Error ? error.message : String(error);
  throw error;
} finally {
  await writeFile(`${output}/results.json`, JSON.stringify({ fixtureOnly: true, passed, errors, failure }, null, 2));
  await browser.close();
}
