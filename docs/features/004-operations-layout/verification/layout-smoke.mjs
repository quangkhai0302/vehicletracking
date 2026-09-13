// UI fixtures only. Every /api/ request is intercepted; no application data is written.
import { chromium } from 'playwright';
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const output = fileURLToPath(new URL('../artifacts/', import.meta.url));
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ channel: 'msedge', headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
const page = await context.newPage();
const errors = [];
page.on('pageerror', (error) => errors.push(error.message));
const checks = [];
const stamp = '2026-09-13T06:00:00Z';
let stations = [
  { id: 1, name: 'Bến Thành', address: 'Đường Lê Lợi, Quận 1', latitude: 10.7723, longitude: 106.6981, checkinRadiusMeters: 50 },
  { id: 2, name: 'Nhà hát Thành phố', address: 'Công trường Lam Sơn, Quận 1', latitude: 10.7765, longitude: 106.7031, checkinRadiusMeters: 50 },
  { id: 3, name: 'Hàng Xanh', address: 'Điện Biên Phủ, Bình Thạnh', latitude: 10.8013, longitude: 106.7118, checkinRadiusMeters: 100 },
  { id: 4, name: 'Thảo Điền', address: 'Xa lộ Hà Nội, TP. Thủ Đức', latitude: 10.8005, longitude: 106.733, checkinRadiusMeters: 80 },
].map((s) => ({ ...s, active: true, createdAt: stamp, updatedAt: stamp }));

function fixturePolyline(points) {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';
  const encode = (value) => { let result = ''; while (value > 31) { result += alphabet[(value & 31) | 32]; value >>>= 5; } return result + alphabet[value]; };
  let previous = [0, 0];
  return 'BF' + points.map((point) => point.map((value, axis) => {
    const scaled = Math.round(value * 1e5);
    const delta = scaled - previous[axis]; previous[axis] = scaled;
    return encode(delta < 0 ? -delta * 2 - 1 : delta * 2);
  }).join('')).join('');
}
const polyline = fixturePolyline([[10.7723,106.6981],[10.774,106.701],[10.7765,106.7031],[10.784,106.704],[10.8013,106.7118],[10.798,106.723],[10.8005,106.733]]);
const detail = {
  id: 1, name: 'Tuyến 01 · Bến Thành – Thảo Điền', transportMode: 'CAR', routingProvider: 'HERE',
  totalDistanceMeters: 8600, estimatedTravelDurationSeconds: 1500, baseTravelDurationSeconds: 1200,
  totalDwellDurationSeconds: 120, estimatedTripDurationSeconds: 1620,
  estimatedDepartureAt: stamp, calculatedAt: stamp, createdAt: stamp,
  stops: stations.map((s, index) => ({ sequenceNumber: index + 1, role: index === 0 ? 'START' : index === 3 ? 'END' : 'STOP', stationId: s.id, stationName: s.name, latitude: s.latitude, longitude: s.longitude, dwellDurationSeconds: index === 1 || index === 2 ? 60 : 0, distanceFromPreviousMeters: index ? 2800 : 0, travelDurationFromPreviousSeconds: index ? 500 : 0, arrivalOffsetSeconds: index * 540, departureOffsetSeconds: index * 540 + (index === 1 || index === 2 ? 60 : 0) })),
  sections: [{ sectionSequence: 1, destinationStopSequence: 4, encodedPolyline: polyline, distanceMeters: 8600, travelDurationSeconds: 1500, baseTravelDurationSeconds: 1200 }],
};
let routes = [{ ...detail, startStationName: 'Bến Thành', endStationName: 'Thảo Điền', stopCount: 4 }];
let mode = 'normal';
let createdRouteRequest;
await context.route('**/api/**', async (route) => {
  const request = route.request();
  const path = new URL(request.url()).pathname;
  const method = request.method();
  const json = (value, status = 200) => route.fulfill({ status, json: value });
  if (mode === 'error') return json({ detail: 'Không thể kết nối dịch vụ (fixture kiểm tra).' }, 503);
  if (method === 'GET' && path.endsWith('/stations')) return json(mode === 'empty' ? [] : stations);
  if (method === 'POST' && path.endsWith('/stations')) {
    const station = { ...request.postDataJSON(), id: 10, active: true, createdAt: stamp, updatedAt: stamp };
    stations.push(station); return json(station, 201);
  }
  if (method === 'PUT' && path.includes('/stations/')) {
    const id = Number(path.split('/').pop());
    const station = { ...stations.find((s) => s.id === id), ...request.postDataJSON() };
    stations = stations.map((s) => s.id === id ? station : s); return json(station);
  }
  if (method === 'DELETE' && path.includes('/stations/')) {
    stations = stations.filter((s) => s.id !== Number(path.split('/').pop()));
    return route.fulfill({ status: 204 });
  }
  if (method === 'GET' && path.endsWith('/routes')) return json(mode === 'empty' ? [] : routes);
  if (method === 'GET' && path.includes('/routes/')) return json(detail);
  if (method === 'POST' && path.endsWith('/routes')) {
    createdRouteRequest = request.postDataJSON();
    const created = { ...detail, id: 2, name: createdRouteRequest.name };
    routes = [{ ...routes[0], ...created }, ...routes]; return json(created, 201);
  }
  return route.abort();
});

const nav = (name) => page.getByRole('navigation').getByRole('button', { name, exact: true }).click();
const screenshot = async (name) => {
  // Let navigation/Leaflet zoom transitions settle before capturing evidence.
  await page.waitForTimeout(450);
  await page.waitForFunction(() => [...document.querySelectorAll('img.leaflet-tile')].every((img) => img.complete), undefined, { timeout: 5000 }).catch(() => {});
  return page.screenshot({ path: `${output}${name}-fixture.png` });
};
async function checkLayout(label, mobile = false) {
  const result = await page.evaluate(() => {
    const rect = (selector) => { const el = document.querySelector(selector); const r = el.getBoundingClientRect(); return { x: r.x, y: r.y, right: r.right, width: r.width, height: r.height }; };
    return { viewport: innerWidth, overflow: document.documentElement.scrollWidth > innerWidth, panels: rect('.workspace-panels'), map: rect('.map-stage') };
  });
  assert.equal(result.overflow, false, `${label}: page overflow`);
  if (!mobile) {
    assert.ok(result.map.x >= result.panels.right, `${label}: map covered by panel`);
    assert.ok(result.map.width >= 250 && result.map.height > 250, `${label}: usable map`);
  }
  checks.push(label);
}

try {
  await page.goto('http://127.0.0.1:5173');
  await page.getByText('Đội xe của bạn sẽ ở đây').waitFor();
  await checkLayout('Desktop tracking 1440');
  await screenshot('tracking-desktop');
  await nav('Quản lý trạm');
  await page.locator('.station-card').first().waitFor();
  await screenshot('stations-desktop');
  await page.getByRole('textbox', { name: 'Tìm kiếm trạm' }).fill('Bến Thành');
  await page.locator('.station-card').first().click();
  await page.getByRole('complementary', { name: 'Chi tiết trạm', exact: true }).waitFor();
  assert.equal(await page.getByRole('complementary', { name: 'Danh sách trạm', exact: true }).isVisible(), false);
  await page.getByRole('button', { name: 'Đóng panel' }).click();
  assert.equal(await page.getByRole('textbox', { name: 'Tìm kiếm trạm' }).inputValue(), 'Bến Thành');
  checks.push('Chi tiết thay danh sách, đóng giữ tìm kiếm');
  await page.getByRole('textbox', { name: 'Tìm kiếm trạm' }).fill('');
  await page.getByRole('button', { name: 'Thêm trạm mới', exact: true }).click();
  await page.getByRole('button', { name: 'Lấy tâm bản đồ', exact: true }).click();
  await page.getByRole('textbox', { name: 'Tên trạm đón trả khách' }).fill('Trạm kiểm tra layout');
  await page.getByRole('button', { name: 'Đóng panel' }).click();
  await page.getByText('Hủy các thay đổi chưa lưu?').waitFor();
  await page.getByRole('button', { name: 'Ở lại', exact: true }).click();
  await screenshot('station-form-desktop');
  await page.locator('.station-form button[type="submit"]').click();
  await page.getByRole('complementary', { name: 'Chi tiết trạm', exact: true }).waitFor();
  await page.getByRole('button', { name: 'Chỉnh sửa', exact: true }).click();
  await page.getByRole('textbox', { name: 'Tên trạm đón trả khách' }).fill('Trạm kiểm tra đã sửa');
  await page.locator('.station-form button[type="submit"]').click();
  await page.getByRole('button', { name: 'Ngừng sử dụng', exact: true }).click();
  await page.getByRole('dialog').waitFor();
  await page.getByRole('button', { name: 'Xác nhận ngừng sử dụng', exact: true }).click();
  await page.getByRole('complementary', { name: 'Danh sách trạm', exact: true }).waitFor();
  checks.push('Tạo/sửa/ngừng sử dụng trạm và xác nhận bỏ form, API fixture');

  await nav('Tuyến đường');
  await page.locator('.route-card').first().click();
  await page.locator('.route-timeline').waitFor();
  await page.locator('.route-stop-map-marker').first().waitFor();
  await screenshot('route-detail-desktop');
  await page.locator('.route-drawer-footer').getByRole('button', { name: 'Đóng', exact: true }).click();
  await page.getByRole('button', { name: 'Tạo tuyến', exact: true }).click();
  await page.getByRole('textbox', { name: 'Tên tuyến đường' }).fill('Tuyến fixture mới');
  await page.getByRole('button', { name: 'Thêm điểm dừng đón/trả' }).click();
  await page.getByRole('spinbutton', { name: 'Thời gian dừng cho điểm 2 (giây)' }).fill('90');
  await page.getByRole('button', { name: 'Lưu tuyến đường', exact: true }).click();
  await page.locator('.route-timeline').waitFor();
  assert.equal(createdRouteRequest.stops[1].dwellDurationSeconds, 90);
  checks.push('Tạo tuyến với dwell, detail, timeline, polyline');

  for (const width of [1024, 768]) {
    await page.setViewportSize({ width, height: 900 });
    await checkLayout(`Route detail ${width}`);
    await screenshot(`route-${width}`);
    const markersFit = await page.locator('.map-canvas').evaluate((el) => {
      const bounds = el.getBoundingClientRect();
      return [...el.querySelectorAll('.route-stop-map-marker')].every((marker) => {
        const point = marker.getBoundingClientRect();
        return point.x >= bounds.x && point.right <= bounds.right && point.y >= bounds.y && point.bottom <= bounds.bottom;
      });
    });
    assert.ok(markersFit, `Resize ${width}: route markers outside map`);
  }
  for (const width of [390, 320]) {
    await page.setViewportSize({ width, height: 844 });
    await nav('Quản lý trạm');
    await page.getByRole('button', { name: 'Danh sách', exact: true }).click();
    await checkLayout(`Mobile stations ${width}`, true);
    await page.getByRole('button', { name: 'Thêm trạm mới', exact: true }).click();
    await page.locator('.map-picking-banner').waitFor();
    const box = await page.locator('.map-canvas').boundingBox();
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
    await page.getByRole('complementary', { name: 'Biểu mẫu trạm' }).waitFor();
    assert.ok(await page.getByRole('spinbutton', { name: 'Vĩ độ' }).inputValue());
    await page.getByRole('textbox', { name: 'Tên trạm đón trả khách' }).fill('Bản nháp mobile');
    await page.getByRole('button', { name: 'Bản đồ', exact: true }).click();
    await page.getByText('Lớp bản đồ', { exact: true }).click();
    await page.getByRole('button', { name: 'Vệ Tinh', exact: true }).click();
    await page.getByRole('button', { name: 'Đường Bộ', exact: true }).click();
    await page.getByText('Lớp bản đồ', { exact: true }).click();
    await screenshot(`map-mobile-${width}`);
    await page.getByRole('button', { name: 'Danh sách', exact: true }).click();
    assert.equal(await page.getByRole('textbox', { name: 'Tên trạm đón trả khách' }).inputValue(), 'Bản nháp mobile');
    await screenshot(`form-mobile-${width}`);
    await page.getByRole('button', { name: 'Đóng panel' }).click();
    await page.getByRole('button', { name: 'Hủy thay đổi', exact: true }).click();
    await nav('Tuyến đường');
    await page.getByRole('button', { name: 'Tạo tuyến', exact: true }).click();
    await page.getByRole('textbox', { name: 'Tên tuyến đường' }).fill('Tuyến mobile');
    await page.getByRole('button', { name: 'Thêm điểm dừng đón/trả' }).click();
    await page.getByRole('button', { name: 'Di chuyển xuống', exact: true }).first().click();
    assert.equal(await page.getByRole('combobox', { name: 'Chọn trạm cho điểm dừng 1' }).inputValue(), '2');
    const formFits = await page.locator('.route-drawer-body').evaluate((el) => el.scrollWidth <= el.clientWidth);
    assert.ok(formFits, `Mobile ${width}: route form horizontal overflow`);
    await screenshot(`route-form-mobile-${width}`);
    await page.getByRole('button', { name: 'Hủy', exact: true }).click();
    await page.locator('.route-card').first().click();
    await page.locator('.route-timeline').waitFor();
    await page.getByRole('button', { name: 'Bản đồ', exact: true }).click();
    await screenshot(`route-map-mobile-${width}`);
    const routeInView = await page.locator('.map-canvas').evaluate((el) => {
      const bounds = el.getBoundingClientRect();
      return [...el.querySelectorAll('.route-stop-map-marker')].every((marker) => {
        const point = marker.getBoundingClientRect();
        return point.x >= bounds.x && point.right <= bounds.right && point.y >= bounds.y && point.bottom <= bounds.bottom;
      });
    });
    assert.ok(routeInView, `Mobile ${width}: route bounds after view switch`);
    await page.getByRole('button', { name: 'Danh sách', exact: true }).click();
    checks.push(`Mobile ${width}: form tuyến, reorder, fit tuyến khi chuyển view`);
    checks.push(`Mobile ${width}: chọn tọa độ, chuyển view giữ form, đổi lớp bản đồ`);
  }
  mode = 'error';
  await page.reload();
  await nav('Quản lý trạm');
  await page.getByRole('alert').waitFor();
  assert.equal(await page.getByText('Chưa có trạm nào', { exact: true }).count(), 0);
  await nav('Tuyến đường');
  await page.getByRole('button', { name: 'Thử lại', exact: true }).waitFor();
  mode = 'empty';
  await page.getByRole('button', { name: 'Thử lại', exact: true }).click();
  await page.getByRole('heading', { name: 'Chưa có tuyến đường nào' }).waitFor();
  checks.push('Lỗi API không hiển thị nhầm empty; retry tuyến về empty');
  assert.deepEqual(errors, []);
  await writeFile(`${output}results.json`, JSON.stringify({ passed: checks, pageErrors: errors, source: 'API fixtures; no live backend or HERE verification' }, null, 2));
  console.log(JSON.stringify({ passed: checks, pageErrors: errors }, null, 2));
} catch (error) {
  await screenshot('failure');
  console.error(error);
  process.exitCode = 1;
} finally {
  await browser.close();
}
