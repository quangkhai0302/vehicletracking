// HISTORICAL: includes the withdrawn road-hover scope. Current read-only check:
// tracking-live-readonly.mjs. Saved screenshots here describe an earlier revision.
// Observe the running application through GET requests only; no fixtures or database writes.
import { chromium } from '../../004-operations-layout/verification/node_modules/playwright/index.mjs';
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
const output = fileURLToPath(new URL('../artifacts/live-readonly/', import.meta.url));
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ channel: 'msedge', headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
page.setDefaultTimeout(15000);
const errors = [], trafficResponses = [], blockedWrites = [];
page.on('pageerror', error => errors.push(error.message));
await page.route('**/api/**', route => {
  if (route.request().method() !== 'GET') { blockedWrites.push(new URL(route.request().url()).pathname); return route.abort(); }
  return route.continue();
});
page.on('response', async response => {
  const path = new URL(response.url()).pathname;
  if (!['/api/v1/traffic/flow', '/api/v1/traffic/incidents'].includes(path)) return;
  try {
    const body = await response.json();
    trafficResponses.push({ path, httpStatus: response.status(), source: body.source, status: body.status, resultCount: body.results?.length });
  } catch { trafficResponses.push({ path, httpStatus: response.status() }); }
});
try {
  await page.goto('http://127.0.0.1:5173', { waitUntil: 'domcontentloaded' });
  const initialHitCount = await page.locator('[data-route-section]').count();
  await page.locator('[data-traffic-road]').first().waitFor({ state: 'attached', timeout: 30000 });
  await page.getByRole('button', { name: 'Thu bảng dữ liệu', exact: true }).click();
  await page.waitForTimeout(400);
  const roadSpots = await page.locator('[data-traffic-road]').evaluateAll(paths => paths.flatMap(path => {
    return [.3,.5,.7].map(fraction => {
      const point = path.getPointAtLength(path.getTotalLength() * fraction);
      const pixel = new DOMPoint(point.x, point.y).matrixTransform(path.getScreenCTM());
      return { x: pixel.x, y: pixel.y, reachable: document.elementFromPoint(pixel.x, pixel.y) === path };
    });
  }));
  const roadSpot = roadSpots.find(spot => spot.reachable && spot.x > 350 && spot.x < 1000 && spot.y > 150 && spot.y < 600)
    ?? roadSpots.find(spot => spot.reachable);
  assert.ok(roadSpot, 'An actual HERE road is reachable before selecting any route');
  await page.mouse.move(roadSpot.x, roadSpot.y);
  const roadCard = page.locator('.route-inspection-card[data-kind="road"]');
  await roadCard.waitFor();
  const roadHover = { beforeRouteSelection: initialHitCount === 0, hitCount: await page.locator('[data-traffic-road]').count(),
    accessibleSpotCount: roadSpots.filter(spot => spot.reachable).length, visible: await roadCard.isVisible(),
    displaysSpeed: await roadCard.locator('dt').filter({hasText:'Tốc độ dòng xe'}).count() > 0 };
  assert.ok(roadHover.beforeRouteSelection && roadHover.displaysSpeed);
  await page.screenshot({ path: output + 'road-hover.png' });
  await page.keyboard.press('Escape');
  await page.getByRole('navigation', { name: 'Chế độ vận hành' }).getByRole('button', { name: 'Tuyến & trạm', exact: true }).click();
  await page.locator('.planning-tabs').getByRole('button', { name: 'Tuyến đường', exact: false }).click();
  const routeCard = page.locator('.route-card').first();
  await routeCard.waitFor(); await routeCard.click();
  await page.locator('[data-route-section]').first().waitFor({ state: 'attached' });
  await page.getByRole('button', { name: 'Thu bảng dữ liệu', exact: true }).click();
  await page.getByRole('button', { name: 'Vừa khung lộ trình', exact: true }).click();
  await page.waitForTimeout(600);
  const spots = await page.locator('[data-route-section]').evaluateAll(paths => paths.flatMap(path => {
    return [.15,.3,.45,.6,.75,.9].map(fraction => {
      const point = path.getPointAtLength(path.getTotalLength() * fraction);
      const pixel = new DOMPoint(point.x, point.y).matrixTransform(path.getScreenCTM());
      return { x: pixel.x, y: pixel.y, section: path.dataset.routeSection,
        reachable: document.elementFromPoint(pixel.x, pixel.y) === path };
    });
  }));
  const accessible = spots.find(spot => spot.reachable);
  if (!accessible) throw new Error('No visible route hit target can receive pointer events');
  await page.mouse.move(accessible.x, accessible.y);
  const card = page.locator('.route-inspection-card');
  await card.waitFor();
  await page.waitForFunction(() => !document.querySelector('.route-inspection-card')?.textContent.includes('Đang tải giao thông'), null, { timeout: 15000 });
  assert.equal(await card.count(), 1, 'Selected route has priority over overlapping road');
  assert.equal(await card.getAttribute('data-kind'), 'route');
  assert.deepEqual(errors, []); assert.deepEqual(blockedWrites, []);
  await page.screenshot({ path: output + 'route-hover.png' });
  const summary = { timestamp: new Date().toISOString(), mode: 'live-readonly', initialHitCount,
    selectedHitCount: await page.locator('[data-route-section]').count(), accessibleSpotCount: spots.filter(spot=>spot.reachable).length,
    hoverVisible: await card.isVisible(), section: accessible.section,
    displaysSpeed: await card.locator('dt').filter({hasText:'Tốc độ dòng xe'}).count() > 0,
    unavailableMessage: await card.locator('.route-inspection-empty').textContent().catch(()=>null),
    roadHover, trafficResponses, pageErrors: errors, blockedWrites };
  await writeFile(output + 'results.json', JSON.stringify(summary, null, 2));
  console.log(JSON.stringify(summary));
} catch (error) { await page.screenshot({ path: output + 'failure.png' }); throw error; }
finally { await browser.close(); }
