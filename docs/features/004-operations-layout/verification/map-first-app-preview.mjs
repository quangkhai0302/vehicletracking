// UI fixtures only. Every /api/ request is intercepted; no application data is written.
import { chromium } from 'playwright';
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const output = fileURLToPath(new URL('../artifacts/map-first-app/', import.meta.url));
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


try {
await page.goto('http://127.0.0.1:5173');
await page.waitForTimeout(1800);
await page.screenshot({path: output + 'desktop-tracking-fixture.png'});
await page.getByRole('navigation').getByRole('button',{name:'Tuyến & trạm',exact:true}).click();
await page.locator('.route-card').first().click();
await page.locator('.route-timeline').waitFor();
await page.waitForTimeout(500);
await page.screenshot({path: output + 'desktop-route-fixture.png'});
console.log(JSON.stringify({errors, map: await page.locator('.map-canvas').boundingBox()}));
} finally {await browser.close();}
