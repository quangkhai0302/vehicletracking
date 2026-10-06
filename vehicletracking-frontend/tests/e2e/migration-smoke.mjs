// Browser functional checks against either frozen React or the Vue candidate.
// All business requests are intercepted; this never contacts a real backend.
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { chromium } from 'playwright';
import { installFixture, stamp } from '../fixtures/api.mjs';

const base = process.env.UI_BASE_URL || 'http://localhost:5173';
const baseUrl = new URL(base);
assert(['127.0.0.1', 'localhost', '[::1]'].includes(baseUrl.hostname) && baseUrl.protocol === 'http:', 'Use a local fixture-only server');
const output = process.env.UI_OUTPUT_DIR || '/tmp/vehicletracking-smoke-032';
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'vi-VN', timezoneId: 'Asia/Ho_Chi_Minh', deviceScaleFactor: 1 });
const state = { role: 'ADMIN', mode: 'data' };
const requests = await installFixture(context, base, state);
const page = await context.newPage();
page.setDefaultTimeout(10_000);
await page.emulateMedia({ reducedMotion: 'reduce' });
// Unlike the pixel suite, leave timers/RAF moving so pointer and native dialog
// interactions run naturally. System time only supplies stable business dates.
await page.clock.install({ time: new Date(stamp) });
const errors = [], passed = [], resourceChecks = [];
let failure = null;
page.on('pageerror', error => errors.push(error.message));
const visit = async path => { await page.goto(base + path); await page.locator('.business-shell,.auth-page,.driver-portal').first().waitFor(); };
async function check(name, action) { await action(); passed.push(name); console.log(`PASS ${name}`); }
async function count(locator, expected) {
  const started = Date.now();
  while (await locator.count() !== expected) {
    assert(Date.now() - started < 10_000, `Expected ${expected} elements for ${locator}`);
    await page.waitForTimeout(25);
  }
}
async function waitForMap() {
  await page.locator('#main-map.leaflet-container').waitFor();
  await page.waitForFunction(() => {
    const tiles = [...document.querySelectorAll('.leaflet-tile')];
    return tiles.length > 0 && tiles.every(tile => tile.complete && tile.naturalWidth > 0 && getComputedStyle(tile).opacity === '1');
  });
}
async function internal(path) {
  // DOM click triggers the application's own router without remounting the app.
  // Bypass only sidebar CSS visibility for lifecycle checks, not modal tests.
  await page.locator(`.business-navigation a[href="${path}"]`).evaluate(element => element.click());
  await page.waitForURL(url => url.pathname === path);
}
async function liveCount(expected) {
  await page.waitForFunction(value => window.__fixtureLiveResources?.active === value, expected);
  const counters = await page.evaluate(() => ({ ...window.__fixtureLiveResources }));
  assert.equal(counters.created - counters.closed, expected); return counters;
}

try {
  await check('admin vehicle editor dirty-discard dialog: focus, Escape and return', async () => {
    await visit('/vehicles'); await page.getByRole('button', { name: 'Thêm phương tiện', exact: true }).click();
    await page.locator('input[name=name]').fill('Unsaved fixture vehicle');
    const closer = page.getByRole('button', { name: 'Đóng biểu mẫu xe', exact: true }); await closer.click();
    const dialog = page.getByRole('dialog'); await dialog.waitFor();
    assert.equal(await dialog.locator('button[autofocus]').evaluate(element => document.activeElement === element), true);
    for (let index = 0; index < 5; index++) {
      await page.keyboard.press('Tab');
      assert.equal(await dialog.evaluate(element => element.contains(document.activeElement) || document.activeElement === document.body), true);
    }
    await page.keyboard.press('Escape'); await dialog.waitFor({ state: 'detached' });
    assert.equal(await page.locator('input[name=name]').inputValue(), 'Unsaved fixture vehicle');
    assert.equal(await closer.evaluate(element => document.activeElement === element), true);
    await closer.click(); await page.getByRole('button', { name: 'Bỏ thay đổi', exact: true }).click();
    await count(page.locator('input[name=name]'), 0);
    assert.equal(requests.some(request => request.path.startsWith('/vehicles') && request.method !== 'GET'), false);
  });

  await check('reports filter requests keep date/vehicle/driver and reset removes IDs', async () => {
    await visit('/reports'); await count(page.locator('.reports-metric'), 7);
    await page.getByLabel('Từ ngày', { exact: true }).fill('2026-09-01');
    await page.getByLabel('Đến ngày', { exact: true }).fill('2026-09-22');
    await page.getByLabel('Phương tiện', { exact: true }).selectOption('1');
    await page.getByLabel('Tài xế', { exact: true }).selectOption('2');
    await count(page.locator('.reports-metric'), 7);
    const selected = requests.filter(request => request.path.startsWith('/reports/operations?')).at(-1);
    const query = new URL('http://fixture' + selected.path).searchParams;
    assert.equal(query.get('from'), '2026-09-01'); assert.equal(query.get('to'), '2026-09-22');
    assert.equal(query.get('vehicleId'), '1'); assert.equal(query.get('driverId'), '2');
    await page.getByRole('button', { name: 'Xóa bộ lọc', exact: true }).click(); await count(page.locator('.reports-metric'), 7);
    const reset = requests.filter(request => request.path.startsWith('/reports/operations?')).at(-1);
    assert.equal(new URL('http://fixture' + reset.path).searchParams.has('vehicleId'), false);
    assert.equal(new URL('http://fixture' + reset.path).searchParams.has('driverId'), false);
  });

  await check('driver role redirect, assigned-only API, detail focus and logout', async () => {
    state.role = 'DRIVER'; const start = requests.length; await visit('/users'); await page.waitForURL('**/driver/today');
    const card = page.locator('.driver-trip-card').first(); await card.waitFor(); await card.click();
    const detail = page.getByRole('dialog', { name: 'Chi tiết chuyến được phân công' }); await detail.waitFor();
    assert.equal(await detail.locator('.driver-stop-list > div').count(), 3);
    await page.keyboard.press('Escape'); await detail.waitFor({ state: 'detached' });
    assert.equal(await card.evaluate(element => document.activeElement === element), true);
    await page.getByRole('link', { name: 'Lịch chạy', exact: true }).click(); await count(page.locator('.driver-schedule-card'), 3);
    await page.goBack(); await page.locator('.driver-trip-card').first().waitFor();
    assert.equal(requests.slice(start).every(request => request.path.startsWith('/auth/') || request.path.startsWith('/driver/')), true);
    await page.locator('.driver-account-trigger').click();
    await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click(); await page.waitForURL('**/login');
  });

  await check('mobile sidebar Escape and schedule dialog stay inside their focus owners', async () => {
    state.role = 'ADMIN'; await page.setViewportSize({ width: 390, height: 844 }); await visit('/schedules');
    const menu = page.getByRole('button', { name: 'Mở điều hướng', exact: true }); await menu.click();
    await page.waitForFunction(() => document.querySelector('.business-sidebar')?.contains(document.activeElement));
    await page.keyboard.press('Escape'); await page.waitForFunction(() => document.activeElement?.getAttribute('aria-label') === 'Mở điều hướng');
    await page.getByRole('button', { name: 'Tạo lịch chạy', exact: true }).first().click();
    const dialog = page.getByRole('dialog', { name: 'Tạo lịch chạy tự động', exact: true }); await dialog.waitFor();
    await dialog.getByRole('button', { name: 'Hủy', exact: true }).scrollIntoViewIfNeeded();
    for (let index = 0; index < 16; index++) {
      await page.keyboard.press('Tab');
      assert.equal(await dialog.evaluate(element => element.contains(document.activeElement) || document.activeElement === document.body), true);
    }
    await page.keyboard.press('Escape'); await dialog.waitFor({ state: 'detached' });
  });

  await check('operations query back/forward preserves map and SSE owner; leaving disposes it', async () => {
    await page.setViewportSize({ width: 1440, height: 1000 }); await visit('/operations'); await waitForMap();
    const before = await liveCount(1);
    await page.locator('#main-map').evaluate(element => { element.dataset.fixtureOwner = 'operations-fixture'; });
    await page.locator('.mode-bar').getByRole('button', { name: 'Mô phỏng', exact: true }).click(); await page.waitForURL('**/operations?mode=simulation');
    await page.goBack(); await page.waitForURL('**/operations'); await page.goForward(); await page.waitForURL('**/operations?mode=simulation');
    assert.equal(await page.locator('#main-map').getAttribute('data-fixture-owner'), 'operations-fixture');
    assert.deepEqual(await liveCount(1), before);
    await internal('/dashboard'); await count(page.locator('.leaflet-container'), 0); resourceChecks.push(await liveCount(0));
  });

  await check('twenty internal map entries leave no active SSE or map container after exit', async () => {
    for (let index = 0; index < 20; index++) {
      const path = ['/operations', '/routes', '/stations'][index % 3];
      await internal(path); await waitForMap(); await liveCount(1);
      await internal('/dashboard'); await count(page.locator('.leaflet-container'), 0); resourceChecks.push(await liveCount(0));
    }
  });

  await check('public guide serves its own HTML rather than the SPA fallback', async () => {
    await page.goto(base + '/huong-dan/index.html');
    assert.equal(await page.locator('.business-shell,.auth-page,.driver-portal').count(), 0);
    assert((await page.locator('body').innerText()).includes('Vehicle'));
  });
  assert.deepEqual(errors, [], 'Browser runtime errors');
} catch (error) {
  failure = error instanceof Error ? error.message : String(error);
  throw error;
} finally {
  await writeFile(`${output}/results.json`, JSON.stringify({ fixtureOnly: true, base, browser: browser.version(), passed, failure, errors, resourceChecks,
    // No auth password/cookie/header values in artifacts, even when fixture-only.
    requests: requests.map(({ method, path }) => ({ method, path })) }, null, 2));
  console.log(JSON.stringify({ output, passed: passed.length, errors })); await browser.close();
}
