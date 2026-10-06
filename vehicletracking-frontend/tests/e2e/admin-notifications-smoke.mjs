// Fixture-only browser checks. All operational and external requests are intercepted.
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { chromium } from 'playwright';
import { installFixture, stamp } from '../fixtures/api.mjs';
import { encode } from '../unit/fixtures/driverNavigation.ts';

const base = process.env.UI_BASE_URL || 'http://localhost:5173';
const baseUrl = new URL(base);
assert(baseUrl.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(baseUrl.hostname),
  'Use a local fixture-only frontend');
const output = process.env.UI_OUTPUT_DIR || '/tmp/vehicletracking-058-browser';
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true });
const passed = [], errors = [];
let failure = null;
function notices() {
  return Array.from({ length: 50 }, (_, index) => ({
    id: index + 1, tripId: 100, vehicleId: 1, vehiclePlateNumber: '51B12340',
    revisionId: index % 3 === 0 ? 11 : null,
    type: ['REROUTE_CREATED', 'DISPATCH_ATTENTION', 'OFF_ROUTE_DETECTED'][index % 3],
    severity: index % 2 ? 'MAJOR' : 'CRITICAL',
    title: index === 0 ? 'Đã tạo tuyến đường thay thế' : `Thông báo kiểm thử #${index + 1}`,
    reason: `Thông tin hành trình và điều phối kiểm thử #${index + 1}.`,
    incidentId: null, affectedStopSequences: '', baselineEtaSeconds: 2700,
    revisedEtaSeconds: 1800, createdAt: stamp, readAt: index < 20 ? null : stamp,
    measuredDistanceMeters: 450, thresholdDistanceMeters: 200, breachDurationSeconds: 90,
  }));
}
try {
  for (const [width, height] of [[1440, 900], [390, 844], [320, 740]]) {
    const context = await browser.newContext({ viewport: { width, height }, locale: 'vi-VN',
      timezoneId: 'Asia/Ho_Chi_Minh', reducedMotion: 'reduce' });
    let page = null, requests = [];
    const state = { rows: notices(), fail: false, deleteFail: true, readCalls: 0, readAllCalls: 0, deleteCalls: 0 };
    try {
      requests = await installFixture(context, base);
      await context.route('**/api/v1/trips/100/revisions/11/comparison', async route => {
        await route.fulfill({ contentType: 'application/json', body: JSON.stringify({
          tripId: 100, revisionId: 11, revisionNumber: 1, createdAt: stamp,
          reason: 'Đã tạo tuyến đường thay thế.', status: 'AVAILABLE', message: null, attemptNumber: 1,
          anchor: { latitude: 10.77, longitude: 106.7 },
          before: { encodedPolylines: [encode([[10.77, 106.7], [10.77, 106.703]])],
            distanceMeters: 2100, durationSeconds: 2700 },
          after: { encodedPolylines: [encode([[10.77, 106.7], [10.77, 106.701],
            [10.771, 106.701], [10.771, 106.702], [10.77, 106.702], [10.77, 106.703]])],
            distanceMeters: 1400, durationSeconds: 1800 },
        }) });
      });
      await context.route('**/api/v1/notifications**', async route => {
        const request = route.request();
        const path = new URL(request.url()).pathname.replace('/api/v1', '');
        requests.push({ method: request.method(), path, headers: request.headers() });
        const json = (body, status = 200) => route.fulfill({ status,
          contentType: 'application/json', body: JSON.stringify(body) });
        if (request.method() === 'GET') return state.fail
          ? json({ detail: 'Fixture unavailable' }, 503) : json(state.rows);
        if (path === '/notifications/read-all') {
          state.readAllCalls++;
          state.rows = state.rows.map(item => ({ ...item, readAt: stamp }));
          return json({ updated: 50 });
        }
        const read = path.match(/^\/notifications\/(\d+)\/read$/);
        if (read) {
          state.readCalls++;
          const item = state.rows.find(item => item.id === Number(read[1]));
          item.readAt = stamp;
          return json(item);
        }
        if (request.method() === 'DELETE') {
          state.deleteCalls++;
          if (state.deleteFail) return json({ detail: 'Fixture conflict' }, 409);
          const id = Number(path.split('/').at(-1));
          state.rows = state.rows.filter(item => item.id !== id);
          return route.fulfill({ status: 204 });
        }
        return json({ detail: 'Unexpected fixture request' }, 400);
      });
      page = await context.newPage();
      page.on('pageerror', error => errors.push(error.stack || error.message));
      await page.clock.install({ time: new Date(stamp) });
      await page.goto(`${base}/dashboard`);
      const bell = page.getByRole('button', { name: 'Thông báo admin', exact: true });
      const panel = page.getByRole('dialog', { name: 'Thông báo', exact: true });
      await bell.waitFor();
      const headerBox = await page.locator('.business-topbar').boundingBox();
      assert(headerBox && headerBox.x >= 0 && headerBox.x + headerBox.width <= width,
        `Header stays within the ${width}px viewport`);
      await page.locator('.admin-notification-badge').waitFor();
      assert.equal(await page.locator('.admin-notification-badge').textContent(), '20');
      assert.equal(await page.locator('a[href="/alerts"]').count(), 0);
      const pageHeight = await page.evaluate(() => document.documentElement.scrollHeight);
      const dashboardTop = await page.locator('.dashboard-page').evaluate(element => element.getBoundingClientRect().top);
      await bell.focus();
      await page.keyboard.press('Enter');
      await panel.waitFor();
      await panel.locator('.admin-notification-card').first().waitFor();
      assert.equal(await panel.locator('.admin-notification-card').count(), 50);
      assert.equal(state.readCalls, 0, 'Opening notifications does not acknowledge them');
      assert.equal(await page.evaluate(() => document.documentElement.scrollHeight), pageHeight);
      assert.equal(await page.locator('.dashboard-page').evaluate(element => element.getBoundingClientRect().top), dashboardTop);
      const geometry = await panel.evaluate(element => {
        const box = element.getBoundingClientRect(), list = element.querySelector('.admin-notification-list');
        return { left: box.left, right: box.right, top: box.top, bottom: box.bottom,
          width: box.width, height: box.height, viewportWidth: innerWidth, viewportHeight: innerHeight,
          scrolls: list.scrollHeight > list.clientHeight };
      });
      assert(geometry.left >= 0 && geometry.right <= geometry.viewportWidth
        && geometry.top >= 0 && geometry.bottom <= geometry.viewportHeight, `Panel stays within ${width}px viewport`);
      assert(geometry.width <= 440 && geometry.height <= 640 && geometry.scrolls);
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
      const headingContrast = await panel.locator('h2').evaluate(element => {
        const rgb = getComputedStyle(element).color.match(/[\d.]+/g).slice(0, 3).map(Number);
        const channels = rgb.map(value => value / 255).map(value => value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4);
        return 1.05 / (channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722 + 0.05);
      });
      assert(headingContrast >= 4.5, `Notification heading remains readable at ${width}px`);
      const first = panel.locator('.admin-notification-card').first();
      assert.equal(await first.getByRole('link', { name: 'Mở giám sát', exact: true }).getAttribute('href'),
        '/operations?tripId=100&revisionId=11');
      await page.screenshot({ path: `${output}/open-${width}.png` });
      const menuPosition = await panel.boundingBox();
      await panel.locator('.admin-notification-list').evaluate(element => { element.scrollTop = 400; });
      await page.clock.runFor(50);
      assert.deepEqual(await panel.boundingBox(), menuPosition, 'Scrolling fifty notifications must not reposition or enlarge the menu');

      const controls = panel.locator('a[href],button:not([disabled]),select:not([disabled])');
      await controls.last().focus();
      await page.keyboard.press('Tab');
      assert.equal(await controls.first().evaluate(element => element === document.activeElement), true);
      await page.keyboard.press('Shift+Tab');
      assert.equal(await controls.last().evaluate(element => element === document.activeElement), true);
      await page.keyboard.press('Escape');
      await panel.waitFor({ state: 'detached' });
      assert.equal(await bell.evaluate(element => element === document.activeElement), true);
      await bell.click();
      await panel.waitFor();
      await panel.getByRole('combobox', { name: 'Loại', exact: true }).selectOption('REROUTE');
      assert.equal(await panel.locator('.admin-notification-card').count(), 17);
      await panel.getByRole('combobox', { name: 'Mức độ', exact: true }).selectOption('CRITICAL');
      assert.equal(await panel.locator('.admin-notification-card').count(), 9);
      await panel.getByRole('combobox', { name: 'Loại', exact: true }).selectOption('ALL');
      await panel.getByRole('combobox', { name: 'Mức độ', exact: true }).selectOption('ALL');
      await first.getByRole('button', { name: 'Đã đọc', exact: true }).click();
      await page.waitForFunction(() => document.querySelector('.admin-notification-badge')?.textContent === '19');
      assert.equal(state.readCalls, 1);
      assert.equal(await first.getByRole('button', { name: 'Đã đọc', exact: true }).count(), 0);
      await panel.getByRole('button', { name: 'Đọc tất cả', exact: true }).click();
      await page.locator('.admin-notification-badge').waitFor({ state: 'detached' });
      assert.equal(state.readAllCalls, 1);
      assert.equal(await panel.getByRole('button', { name: 'Đọc tất cả', exact: true }).isDisabled(), true);

      await first.getByRole('button', { name: 'Xóa', exact: true }).click();
      const confirm = page.locator('dialog[open]');
      await confirm.waitFor();
      assert.equal(state.deleteCalls, 0);
      await confirm.getByRole('button', { name: 'Xác nhận xóa', exact: true }).click();
      await confirm.getByRole('alert').waitFor();
      assert.match(await confirm.innerText(), /HTTP 409/);
      assert.equal(await panel.locator('.admin-notification-card').count(), 50);
      state.deleteFail = false;
      await confirm.getByRole('button', { name: 'Xác nhận xóa', exact: true }).click();
      await confirm.waitFor({ state: 'detached' });
      assert.equal(state.deleteCalls, 2);
      assert.equal(await panel.locator('.admin-notification-card').count(), 49);
      await page.screenshot({ path: `${output}/read-${width}.png` });

      state.fail = true;
      await panel.getByRole('button', { name: 'Làm mới thông báo admin', exact: true }).click();
      await panel.getByRole('alert').waitFor();
      assert.match(await panel.getByRole('alert').innerText(), /HTTP 503/);
      assert.equal(await page.locator('.admin-notification-error-dot').count(), 1);
      state.fail = false;
      await panel.getByRole('button', { name: 'Thử lại', exact: true }).click();
      await panel.getByRole('alert').waitFor({ state: 'detached' });
      assert.equal(await panel.locator('.admin-notification-card').count(), 49);
      await panel.getByRole('button', { name: 'Đóng thông báo admin', exact: true }).click();
      await panel.waitFor({ state: 'detached' });
      assert.equal(await bell.evaluate(element => element === document.activeElement), true);
      await bell.click();
      await panel.waitFor();
      await page.mouse.click(width / 2, height - 10);
      await panel.waitFor({ state: 'detached' });
      passed.push(`50-card scrolling menu, keyboard/outside, filters, read/read-all, confirmed delete retry and fetch retry at ${width}px`);
      console.log(`PASS ${passed.at(-1)}`);

      state.rows = [{ ...notices()[0], id: 51, title: 'Thông báo mới', readAt: null }, ...state.rows].slice(0, 50);
      await page.clock.runFor(15_000);
      await page.locator('.admin-notification-badge').waitFor();
      assert.equal(await page.locator('.admin-notification-badge').textContent(), '1');
      await bell.click();
      await panel.waitFor();
      const readsBeforeRoute = requests.filter(request => request.method === 'GET' && request.path.startsWith('/notifications')).length;
      const liveResources = await page.evaluate(() => ({ ...window.__fixtureLiveResources }));
      await panel.getByRole('link', { name: 'Mở giám sát', exact: true }).first().click();
      await panel.waitFor({ state: 'detached' });
      await page.locator('#main-map.leaflet-container').waitFor();
      const mapBox = await page.locator('#main-map').boundingBox();
      assert(mapBox && mapBox.x >= 0 && mapBox.x + mapBox.width <= width,
        `Map grid must stay within the ${width}px viewport after showing the global bell`);
      assert.equal(new URL(page.url()).pathname + new URL(page.url()).search, '/operations?tripId=100&revisionId=11');
      assert.equal(await bell.isVisible(), true, 'The compact map header includes the same global bell');
      assert.equal(await page.locator('.admin-notification-badge').textContent(), '1');
      assert.equal(requests.filter(request => request.method === 'GET' && request.path.startsWith('/notifications')).length,
        readsBeforeRoute, 'Navigation must not recreate the notification polling source');
      assert.deepEqual(await page.evaluate(() => ({ ...window.__fixtureLiveResources })), liveResources);
      await bell.click();
      await panel.waitFor();
      await panel.locator('.admin-notification-card').first().waitFor();
      assert.equal(await panel.locator('.admin-notification-card').count(), 50);
      await page.screenshot({ path: `${output}/map-open-${width}.png` });
      passed.push(`Navigation closes the popover and preserves badge, notifications and the global map bell at ${width}px`);
      console.log(`PASS ${passed.at(-1)}`);

      await panel.locator('.admin-notification-card').first().getByRole('button', { name: 'Xóa', exact: true }).click();
      await confirm.waitFor();
      const deleteCallsBeforeBack = state.deleteCalls;
      await page.goBack();
      await confirm.waitFor({ state: 'detached' });
      await panel.waitFor({ state: 'detached' });
      assert.equal(new URL(page.url()).pathname, '/dashboard');
      assert.equal(state.deleteCalls, deleteCallsBeforeBack, 'Back navigation dismisses confirmation without deleting the notification');
      assert.equal(await page.locator('.admin-notification-badge').textContent(), '1');
      assert.equal(requests.filter(request => request.method === 'GET' && request.path.startsWith('/notifications')).length,
        readsBeforeRoute, 'Back navigation keeps the same polling source');
      await page.goForward();
      await page.locator('#main-map.leaflet-container').waitFor();
      await bell.click();
      await panel.waitFor();

      if (width === 1440) {
        state.rows = [];
        await panel.getByRole('button', { name: 'Làm mới thông báo admin', exact: true }).click();
        await panel.getByText('Chưa có thông báo', { exact: true }).waitFor();
        await page.screenshot({ path: `${output}/empty.png` });
        await page.goto(`${base}/alerts`);
        await panel.waitFor();
        assert.equal(new URL(page.url()).pathname, '/dashboard');
        assert.equal(new URL(page.url()).searchParams.get('notifications'), 'open');
        const legacyPanel = await panel.boundingBox();
        assert(legacyPanel && legacyPanel.x >= 0 && legacyPanel.y >= 0
          && legacyPanel.x + legacyPanel.width <= width && legacyPanel.y + legacyPanel.height <= height,
        'Menu opened during legacy redirect must be positioned after its trigger mounts');
        await panel.getByRole('button', { name: 'Đóng thông báo admin', exact: true }).click();
        await panel.waitFor({ state: 'detached' });
        assert.equal(new URL(page.url()).searchParams.has('notifications'), false);
        passed.push('Empty state and legacy /alerts redirect open the bell and clear only its menu query on close');
        console.log(`PASS ${passed.at(-1)}`);
      }
      for (const request of requests.filter(request => request.method === 'POST' || request.method === 'DELETE')) {
        assert(request.path.startsWith('/notifications/'), 'Only requested notification acknowledgements or dismissals may write');
        assert.equal(request.headers['x-xsrf-token'], 'fixture-csrf');
      }
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
