import { chromium } from 'playwright';
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { installFixture, routeDetail } from '../fixtures/api.mjs';
import { encode } from '../unit/fixtures/driverNavigation.ts';

// UI-only verification: API responses and provider tiles are controlled fixtures.
const base = process.env.FRONTEND_URL ?? 'http://127.0.0.1:5173';
const output = process.env.ROUTE_MAP_OUTPUT_DIR ?? '/tmp/vehicletracking-055-browser';
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true });
const failures = [],
  results = [];
const section = (destinationStopSequence, points) => ({
  sectionSequence: destinationStopSequence - 1,
  destinationStopSequence,
  encodedPolyline: encode(points),
  distanceMeters: 1500,
  travelDurationSeconds: 600,
  baseTravelDurationSeconds: 600,
});
const detail = {
  ...routeDetail,
  sections: [
    section(2, [
      [10.77, 106.7],
      [10.774, 106.7],
      [10.774, 106.71],
      [10.78, 106.71],
    ]),
    section(3, [
      [10.78, 106.71],
      [10.785, 106.71],
      [10.785, 106.72],
      [10.79, 106.72],
    ]),
  ],
};

async function addGuidePoint(page) {
  await page.evaluate(
    () => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))),
  );
  const path = page.locator('path[stroke="#4285f4"]').first();
  // Use the middle of a real SVG segment, not the path bounding-box center.
  const point = await path.evaluate((el) => {
    const position = el.getPointAtLength(el.getTotalLength() * 0.45);
    const screen = new DOMPoint(position.x, position.y).matrixTransform(el.getScreenCTM());
    return { x: screen.x, y: screen.y };
  });
  await page.mouse.move(point.x, point.y);
  await page.mouse.down();
  await page.mouse.move(point.x + 30, point.y + 15, { steps: 6 });
  await page.mouse.up();
  await page.locator('.route-shape-handle').waitFor();
}
async function verify(viewport) {
  const context = await browser.newContext({ viewport });
  const requests = await installFixture(context, base);
  let saved = detail;
  await context.route('**/api/v1/routes/**', async (route) => {
    const request = route.request(),
      path = new URL(request.url()).pathname.replace('/api/v1', '');
    requests.push({ method: request.method(), path, body: request.postData() });
    const json = (body, status = 200) =>
      route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
    if (request.method() === 'GET') return json(saved);
    const input = request.postDataJSON();
    assert(input.points.length > 0);
    const changed = {
      ...detail,
      totalDistanceMeters: 27000,
      shapingPoints: input.points,
      sections: [
        section(2, [
          [10.77, 106.7],
          ...input.points
            .filter((point) => point.destinationStopSequence === 2)
            .map((point) => [point.latitude, point.longitude]),
          [10.78, 106.71],
        ]),
        detail.sections[1],
      ],
    };
    if (path.endsWith('/preview')) return json(changed);
    if (path.endsWith('/copy')) {
      saved = { ...changed, id: 2, name: 'Tuyến mới từ bản đồ' };
      return json(saved);
    }
    return json({ detail: 'Tuyến đã có chuyến đi; hãy lưu thành tuyến mới.' }, 409);
  });
  const page = await context.newPage();
  page.on('pageerror', (error) => failures.push(error.message));
  try {
    await page.goto(`${base}/routes?routeId=1`);
    const modal = page.locator('.route-map-dialog[open]');
    await modal.waitFor();
    await modal.locator('.leaflet-marker-icon').first().waitFor();
    assert.equal(await modal.locator('.leaflet-marker-icon').count(), 3);
    assert.equal(await modal.locator('path[stroke="#0ea5e9"]').count(), 2);
    assert.equal(await modal.locator('.route-detail-stop-card').count(), 0);
    const originalPath = await modal.locator('path[stroke="#0ea5e9"]').first().getAttribute('d');
    assert.equal(
      await modal
        .locator('.leaflet-control-zoom-in')
        .evaluate((el) => getComputedStyle(el).backgroundColor),
      'rgb(255, 255, 255)',
    );
    await modal.locator('.leaflet-marker-icon').first().click();
    await modal.locator('.leaflet-popup-content').waitFor();
    assert.equal(
      await modal
        .locator('.leaflet-popup-content-wrapper')
        .evaluate((el) => getComputedStyle(el).backgroundColor),
      'rgb(255, 255, 255)',
    );
    await modal.locator('.leaflet-popup-close-button').click();
    await modal.locator('.leaflet-popup').waitFor({ state: 'detached' });
    await modal.getByRole('button', { name: 'Xem toàn tuyến trên bản đồ', exact: true }).click();
    const box = await modal.boundingBox();
    assert(
      box &&
        box.x >= 0 &&
        box.y >= 0 &&
        box.x + box.width <= viewport.width &&
        box.y + box.height <= viewport.height,
    );
    await page.screenshot({ path: `${output}/view-${viewport.width}.png` });
    await modal.getByRole('button', { name: 'Sửa tuyến đường', exact: true }).click();
    await modal.locator('.route-drawer').waitFor();
    await addGuidePoint(page);
    assert.equal(await modal.locator('.leaflet-container').count(), 1);
    assert.equal(await modal.locator('path[stroke="#0ea5e9"]').count(), 0);
    await page.screenshot({ path: `${output}/edit-${viewport.width}.png` });
    await page.keyboard.press('Escape');
    const confirmation = page.locator('.fleet-confirm[open]');
    await confirmation.waitFor();
    await confirmation.getByRole('button', { name: 'Quay lại', exact: true }).click();
    await modal.getByRole('button', { name: 'Hủy', exact: true }).click();
    await confirmation.getByRole('button', { name: 'Bỏ thay đổi', exact: true }).click();
    assert.equal(await modal.locator('.route-drawer').count(), 0);
    assert.equal(await modal.count(), 1);
    await modal.getByRole('button', { name: 'Sửa tuyến đường', exact: true }).click();
    await addGuidePoint(page);
    await modal.getByRole('button', { name: 'Tính lại tuyến', exact: true }).click();
    await modal.getByText('27.00 km', { exact: false }).waitFor();
    await modal.getByRole('button', { name: 'Lưu tuyến', exact: true }).click();
    await page
      .getByText('Tuyến đã có chuyến đi; hãy lưu thành tuyến mới.', { exact: true })
      .waitFor();
    assert.equal(await modal.locator('.route-shape-handle').count(), 1);
    await modal.getByLabel('Lưu thành tuyến mới', { exact: true }).check();
    await modal.getByRole('button', { name: 'Lưu tuyến', exact: true }).click();
    await page.waitForURL('**/routes?routeId=2');
    await modal.getByRole('heading', { name: 'Tuyến mới từ bản đồ', exact: true }).waitFor();
    await modal.getByText('27.0 km', { exact: true }).waitFor();
    assert.notEqual(
      await modal.locator('path[stroke="#0ea5e9"]').first().getAttribute('d'),
      originalPath,
    );
    assert.equal(await page.locator('.route-management-section tbody tr').count(), 2);
    assert.equal(await modal.evaluate((el) => el.scrollWidth <= el.clientWidth), true);
    await modal.getByRole('button', { name: 'Tạo chuyến từ tuyến này', exact: true }).click();
    await page.waitForURL('**/trips?routeId=2&create=1');
    assert.equal(await page.locator('.route-map-dialog').count(), 0);
    assert.equal(requests.filter((r) => r.path.endsWith('/shape/preview')).length, 1);
    assert.equal(requests.filter((r) => r.path.endsWith('/shape/copy')).length, 1);
    results.push({ viewport, passed: true, requestCount: requests.length });
  } finally {
    await context.close();
  }
}
try {
  await verify({ width: 1440, height: 1000 });
  await verify({ width: 390, height: 844 });
  assert.deepEqual(failures, []);
  console.log(JSON.stringify({ results, failures }, null, 2));
} finally {
  await writeFile(`${output}/results.json`, JSON.stringify({ results, failures }, null, 2));
  await browser.close();
}
