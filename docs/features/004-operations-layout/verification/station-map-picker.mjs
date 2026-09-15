// Focused UI fixture: verifies that station picking keeps the Leaflet map draggable.
import { chromium } from './node_modules/playwright/index.mjs';
import assert from 'node:assert/strict';

const browser = await chromium.launch({ executablePath: '/usr/bin/google-chrome', headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
const errors = [];
page.on('pageerror', error => errors.push(error.message));
const now = new Date().toISOString();
const snapshot = { serverTime: now, positions: [], simulations: [], trips: [], checkIns: [], notifications: [] };

await page.route('**/api/v1/**', async route => {
  const url = new URL(route.request().url());
  if (url.pathname.endsWith('/telemetry/stream')) {
    return route.fulfill({ status: 200, contentType: 'text/event-stream', body: `event: snapshot\ndata: ${JSON.stringify(snapshot)}\n\n` });
  }
  if (url.pathname.endsWith('/telemetry/snapshot')) return route.fulfill({ json: snapshot });
  if (url.pathname.endsWith('/stations')) return route.fulfill({ json: [] });
  if (url.pathname.endsWith('/routes')) return route.fulfill({ json: [] });
  if (url.pathname.endsWith('/vehicles')) return route.fulfill({ json: [] });
  if (url.pathname.endsWith('/trips')) return route.fulfill({ json: [] });
  return route.fulfill({ status: 404, json: { detail: 'Fixture endpoint not required.' } });
});

try {
  await page.goto(process.env.VERIFICATION_APP || 'http://127.0.0.1:5173', { waitUntil: 'domcontentloaded' });
  await page.getByRole('button', { name: 'Tuyến & trạm', exact: true }).click();
  await page.getByRole('button', { name: /Trạm dừng/ }).click();
  await page.getByRole('button', { name: 'Thêm trạm mới', exact: true }).click();

  const banner = page.locator('.map-picking-banner');
  const target = page.locator('.station-center-target');
  await banner.waitFor();
  await target.waitFor();
  const bannerBox = await banner.boundingBox();
  const mapBox = await page.locator('.map-canvas').boundingBox();
  const targetBefore = await target.boundingBox();
  assert.ok(bannerBox && mapBox && targetBefore);
  assert.ok(bannerBox.height < 90, `Picker banner unexpectedly covers the map: ${bannerBox.height}px`);

  const hit = await page.evaluate(({ x, y }) => document.elementFromPoint(x, y)?.closest('.map-picking-banner') !== null,
    { x: targetBefore.x + targetBefore.width / 2, y: targetBefore.y + targetBefore.height / 2 });
  assert.equal(hit, false, 'Picker banner blocks the map center');

  await page.getByRole('button', { name: 'Chọn vị trí này', exact: true }).click();
  await page.getByRole('complementary', { name: 'Biểu mẫu trạm', exact: true }).waitFor();
  await page.getByText('Nâng cao · tọa độ và vùng check-in', { exact: true }).click();
  const latitudeInput = page.getByRole('spinbutton', { name: 'Vĩ độ (Latitude)' });
  const longitudeInput = page.getByRole('spinbutton', { name: 'Kinh độ (Longitude)' });
  const baseline = { latitude: Number(await latitudeInput.inputValue()), longitude: Number(await longitudeInput.inputValue()) };
  assert.ok(Number.isFinite(baseline.latitude) && Number.isFinite(baseline.longitude));

  await page.getByRole('button', { name: 'Chọn lại vị trí trên bản đồ', exact: true }).click();
  await banner.waitFor();
  await target.waitFor();
  await page.waitForTimeout(300);
  const settledTarget = await target.boundingBox();
  assert.ok(settledTarget);
  const dragStart = { x: mapBox.x + Math.min(360, mapBox.width * 0.25), y: mapBox.y + mapBox.height * 0.55 };
  await page.mouse.move(dragStart.x, dragStart.y);
  await page.mouse.down();
  await page.mouse.move(dragStart.x - 180, dragStart.y, { steps: 12 });
  await page.mouse.up();
  await page.waitForTimeout(300);
  const targetAfter = await target.boundingBox();
  assert.ok(targetAfter);
  assert.ok(Math.abs(targetAfter.x - settledTarget.x) < 2 && Math.abs(targetAfter.y - settledTarget.y) < 2,
    'The target must remain fixed while the map moves underneath it');

  await page.getByRole('button', { name: 'Chọn vị trí này', exact: true }).click();
  await page.getByRole('complementary', { name: 'Biểu mẫu trạm', exact: true }).waitFor();
  const latitude = Number(await latitudeInput.inputValue());
  const longitude = Number(await longitudeInput.inputValue());
  assert.ok(Number.isFinite(latitude) && Number.isFinite(longitude));
  assert.ok(Math.abs(longitude - baseline.longitude) > 0.0005,
    `Dragging the map did not change the selected center (${baseline.longitude} -> ${longitude})`);

  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole('button', { name: 'Chọn lại vị trí trên bản đồ', exact: true }).click();
  await banner.waitFor();
  await target.waitFor();
  await page.waitForTimeout(300);
  const mobileBanner = await banner.boundingBox();
  const mobileMap = await page.locator('.map-canvas').boundingBox();
  const mobileTarget = await target.boundingBox();
  assert.ok(mobileBanner && mobileMap && mobileTarget);
  assert.ok(mobileBanner.height < 120 && mobileBanner.width <= 358,
    `Mobile picker is too large: ${mobileBanner.width}x${mobileBanner.height}`);
  const mobileStart = { x: mobileMap.x + mobileMap.width / 2, y: mobileMap.y + mobileMap.height * 0.65 };
  await page.mouse.move(mobileStart.x, mobileStart.y);
  await page.mouse.down();
  await page.mouse.move(mobileStart.x + 100, mobileStart.y - 40, { steps: 10 });
  await page.mouse.up();
  await page.waitForTimeout(300);
  await page.getByRole('button', { name: 'Chọn vị trí này', exact: true }).click();
  await page.getByRole('complementary', { name: 'Biểu mẫu trạm', exact: true }).waitFor();
  const mobileLatitude = Number(await latitudeInput.inputValue());
  const mobileLongitude = Number(await longitudeInput.inputValue());
  assert.ok(Math.abs(mobileLatitude - latitude) > 0.0001 || Math.abs(mobileLongitude - longitude) > 0.0001,
    'Dragging the mobile map did not change the selected center');
  assert.deepEqual(errors, []);
  console.log(JSON.stringify({ passed: ['compact banner', 'desktop map drag', 'fixed center target', 'center coordinates accepted', 'mobile map drag'], latitude, longitude, mobileLatitude, mobileLongitude }));
} finally {
  await browser.close();
}
