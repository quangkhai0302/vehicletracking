import assert from 'node:assert/strict';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { chromium } from 'playwright';
import { installFixture, stamp } from '../fixtures/api.mjs';

const base = process.env.UI_BASE_URL || 'http://localhost:5173';
const baseUrl = new URL(base);
assert(['127.0.0.1', 'localhost', '[::1]'].includes(baseUrl.hostname) && baseUrl.protocol === 'http:', 'Capture requires a local fixture-only server');
const output = process.env.UI_OUTPUT_DIR || '/tmp/vehicletracking-vue-visual-032';
const fontCache = process.env.UI_FONT_CACHE || '/tmp/vehicletracking-fonts-032';
const fontCssUrl = 'https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap';
await mkdir(output, { recursive: true });
await mkdir(fontCache, { recursive: true });
const cachePath = url => `${fontCache}/${createHash('sha256').update(url).digest('hex')}`;
async function fontBytes(url) {
  try { return await readFile(cachePath(url)); } catch {
    const response = await fetch(url, { headers: { 'User-Agent': 'Mozilla/5.0 Chrome/140.0.0.0 Safari/537.36' } });
    if (!response.ok) throw new Error(`Font fetch HTTP ${response.status}`);
    const bytes = Buffer.from(await response.arrayBuffer());
    await writeFile(cachePath(url), bytes); return bytes;
  }
}
const fontCss = await fontBytes(fontCssUrl);
for (const match of fontCss.toString().matchAll(/url\((https:[^)]+)\)/g)) await fontBytes(match[1]);
if (process.argv.includes('--fonts-only')) { console.log('Inter font fixture cached'); process.exit(0); }
const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'vi-VN', timezoneId: 'Asia/Ho_Chi_Minh', deviceScaleFactor: 1 });
const state = { role: 'ADMIN', mode: 'data' };
const requests = await installFixture(context, base, state);
await context.route('https://fonts.googleapis.com/**', route => route.fulfill({ contentType: 'text/css', body: fontCss }));
await context.route('https://fonts.gstatic.com/**', async route => route.fulfill({ contentType: 'font/woff2', body: await fontBytes(route.request().url()) }));
const page = await context.newPage();
await page.emulateMedia({ reducedMotion: 'reduce' });
await page.clock.install({ time: new Date(stamp) });
await page.clock.pauseAt(new Date(Date.parse(stamp) + 1000));
const errors = [], shots = [], metrics = {}, tileStates = {};
let failure = null, activeShot = null;
page.on('pageerror', error => errors.push(error.message));
const businessOnly = process.env.UI_SCOPE === 'business';
const mapsOnly = process.env.UI_SCOPE === 'maps';
const adminPaths = mapsOnly ? ['/operations', '/routes', '/stations'] : businessOnly ? ['/dashboard', '/vehicles', '/drivers', '/trips', '/schedules', '/alerts', '/reports', '/users'] : ['/dashboard', '/vehicles', '/drivers', '/trips', '/schedules', '/alerts', '/reports', '/users', '/operations', '/routes', '/stations'];
async function capture(name) {
  activeShot = name;
  await page.evaluate(() => document.fonts.ready);
  assert(await page.evaluate(() => document.fonts.check('400 14px Inter')), 'Inter must load');
  await page.waitForTimeout(300);
  if (await page.locator('.business-map-page').count()) {
    // Lazy CSS, tile decoding and Leaflet's JS fade must finish before freezing a shot.
    // A loaded shell alone is not evidence that the asynchronous map is ready.
    let ready = false;
    for (let attempt = 0; attempt < 40; attempt++) {
      await page.clock.runFor(250);
      ready = await page.evaluate(() => {
        const tiles = [...document.querySelectorAll('.leaflet-tile')];
        return tiles.length > 0 && tiles.every(tile => tile.complete && tile.naturalWidth > 0 && getComputedStyle(tile).opacity === '1');
      });
      if (ready) break;
      await page.waitForTimeout(50);
    }
    if (!ready) {
      // Preserve evidence even when readiness fails before the first screenshot.
      const state = await page.evaluate(() => ({ mapMounted: !!document.querySelector('#main-map.leaflet-container'),
        loadingText: document.querySelector('.business-map-loading')?.textContent,
        time: new Date().toISOString(), tiles: [...document.querySelectorAll('.leaflet-tile')].map(tile => ({
          complete: tile.complete, naturalWidth: tile.naturalWidth, opacity: getComputedStyle(tile).opacity, className: tile.className,
        })) }));
      await writeFile(`${output}/map-readiness-failure.json`, JSON.stringify({ name, state }, null, 2));
      assert.fail('Map fixture tiles must decode and fade in naturally before capture');
    }
  }
  await page.clock.runFor(1000);
  await page.mouse.move(0, 0);
  await page.screenshot({ path: `${output}/${name}.png`, fullPage: true, animations: 'disabled' });
  shots.push(name);
  metrics[name] = await page.evaluate(() => [...document.querySelectorAll('.business-sidebar,.business-topbar,.business-content,.map-canvas,.workspace-context,.fleet-workspace,.management-table,dialog[open],.auth-card,.driver-portal')].map(el => {
    const { x, y, width, height } = el.getBoundingClientRect(); const css = getComputedStyle(el);
    return { class: el.className, x, y, width, height, color: css.color, backgroundColor: css.backgroundColor, font: css.font, borderColor: css.borderColor };
  }));
  tileStates[name] = await page.evaluate(() => [...document.querySelectorAll('.leaflet-tile')].map(el => ({ complete: el.complete, naturalWidth: el.naturalWidth,
    className: el.className, opacity: getComputedStyle(el).opacity, visibility: getComputedStyle(el).visibility, parentOpacity: getComputedStyle(el.parentElement).opacity })));
}
async function visit(path) {
  await page.clock.setSystemTime(new Date(stamp));
  await page.goto(base + path);
  await page.locator('.business-shell,.auth-page,.driver-portal').first().waitFor();
}
try {
  for (const width of [1440, 768, 390]) {
    await page.setViewportSize({ width, height: width === 390 ? 844 : 1000 });
    for (const path of [...adminPaths, ...(mapsOnly ? [] : ['/driver/today', '/driver/schedules', '/login', '/register', '/unknown-fixture'])]) {
      state.role = path.startsWith('/driver/') ? 'DRIVER' : ['/login', '/register'].includes(path) ? 'GUEST' : 'ADMIN';
      await visit(path); await capture(`${path.slice(1).replaceAll('/', '-')}-${width}`);
    }
  }
  state.role = 'ADMIN';
  for (const [width, height] of businessOnly ? [] : [[320, 844], [844, 390]]) {
    await page.setViewportSize({ width, height });
    for (const path of ['/operations', '/operations?mode=simulation', '/routes', '/stations']) {
      await visit(path); await capture(`${path.slice(1).replaceAll('?', '-')}-${width}`);
    }
  }
  await page.setViewportSize({ width: 1440, height: 1000 });
  for (const mode of ['empty', 'error']) {
    state.mode = mode;
    for (const path of adminPaths.filter(path => !['/operations', '/routes', '/stations'].includes(path))) {
      await visit(path); await capture(`${path.slice(1)}-${mode}`);
    }
  }
  state.mode = 'data';
  for (const [path, button, name] of [
    ['/vehicles', 'Thêm phương tiện', 'vehicle-editor'], ['/drivers', 'Thêm tài xế', 'driver-editor'],
    ['/trips', 'Tạo chuyến đi', 'trip-editor'], ['/schedules', 'Tạo lịch chạy', 'schedule-editor'],
  ]) {
    if (!adminPaths.includes(path)) continue;
    await visit(path); await page.getByRole('button', { name: button, exact: true }).first().click();
    await capture(name);
  }
} catch (error) {
  failure = error instanceof Error ? error.message : String(error);
  throw error;
} finally {
  await writeFile(`${output}/metrics.json`, JSON.stringify(metrics, null, 2));
  await writeFile(`${output}/tiles.json`, JSON.stringify(tileStates, null, 2));
  await writeFile(`${output}/results.json`, JSON.stringify({ fixtureOnly: true, browser: browser.version(), interLoaded: true, tileFixture: 'deterministic SVG, no map masking', shots, activeShot, failure, errors, requests }, null, 2));
  console.log(JSON.stringify({ output, shots: shots.length, errors }));
  await browser.close();
}
assert.equal(errors.length, 0, 'Browser runtime errors');
