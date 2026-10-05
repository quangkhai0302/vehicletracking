import { chromium } from 'playwright';
import assert from 'node:assert/strict';

// Isolated UI smoke: all backend responses are fixtures, no real trips are changed.
const base = process.env.FRONTEND_URL ?? 'http://127.0.0.1:4185';
const browser = await chromium.launch({ headless: true });
const errors = [];
const retiredRequests = [];
const departure = new Date(Date.now() + 10 * 60_000).toISOString();
const cutoff = new Date(Date.now() + 25 * 60_000).toISOString();
const trip = {
  id: 7, vehicleId: 2, vehiclePlateNumber: '51B12345', vehicleType: 'CAR',
  routeId: 3, routeName: 'Tuyến điều phối', status: 'SCHEDULED',
  scheduledDepartureAt: departure, plannedEndAt: cutoff,
  startedAt: null, endedAt: null, createdAt: new Date().toISOString(),
  dispatchMode: 'FIXED_SCHEDULE', scheduleId: 4, scheduleName: 'Lịch thử',
  driver: { id: 1, fullName: 'Tài xế 1', licenseNumber: 'B2-001', active: true },
};
const state = { accepted: false, read: false, acceptCalls: 0 };

async function openDriver(driverId, width) {
  const context = await browser.newContext({ viewport: { width, height: 844 },
    isMobile: width < 700, hasTouch: width < 700 });
  await context.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace('/api/v1', '');
    if (/trips\/.*\/dispatch|dispatch\/offers|readiness|operating-depot|depot-return/.test(path)) retiredRequests.push(path);
    const json = (value, status = 200) => route.fulfill({ status,
      contentType: 'application/json', body: JSON.stringify(value) });
    if (path === '/auth/me') return json({ accountId: driverId, username: `driver${driverId}`,
      role: 'DRIVER', active: true, passwordChangeRequired: false, driverId, driverName: `Tài xế ${driverId}` });
    if (path === '/auth/csrf') return route.fulfill({ contentType: 'application/json',
      headers: { 'set-cookie': 'XSRF-TOKEN=fixture-csrf; Path=/; SameSite=Lax' }, body: '{}' });
    if (path === '/driver/schedules') return json([]);
    if (path === '/driver/trips') return json(driverId === 1 ? [trip] : []);
    if (path === '/driver/assignment-requests') return json(driverId === 2 && !state.accepted ? [{
      requestId: 'request-8', tripId: 8, routeName: 'Tuyến tức thời', vehiclePlate: '51B12345',
      tripCreatedAt: new Date().toISOString(), requestedAt: new Date().toISOString(),
    }] : []);
    if (path === '/driver/assignment-requests/request-8/accept') {
      state.acceptCalls++; state.accepted = true;
      return json({ tripId: 8, requestId: 'request-8', status: 'ACCEPTED' });
    }
    if (path === '/driver/dispatch/inbox') return json(driverId === 2 ? [{
      id: 9, type: 'DIRECT_ASSIGNMENT_REQUESTED', title: 'Yêu cầu nhận chuyến #8',
      detail: 'Tuyến tức thời', tripId: 8, offerId: null, assignmentRequestId: 'request-8',
      createdAt: new Date().toISOString(), readAt: state.read ? new Date().toISOString() : null,
    }] : []);
    if (path === '/driver/dispatch/inbox/9/read') {
      state.read = true;
      return json({ id: 9, title: 'Yêu cầu nhận chuyến #8', readAt: new Date().toISOString() });
    }
    return json({ detail: `Unhandled fixture endpoint ${path}` }, 404);
  });
  const page = await context.newPage();
  page.on('pageerror', error => errors.push(error.message));
  await page.goto(`${base}/driver/today`);
  await page.locator('.driver-dispatch-workspace').waitFor();
  return { context, page };
}

try {
  const primary = await openDriver(1, 390);
  await primary.page.locator('.driver-trip-card').first().waitFor();
  assert.equal(await primary.page.getByRole('button', { name: 'Sẵn sàng', exact: true }).count(), 0);
  assert.equal(await primary.page.getByRole('button', { name: 'Báo bận', exact: true }).count(), 0);
  assert.equal(await primary.page.locator('.driver-dispatch-cards').count(), 0);
  assert.equal(await primary.page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
  await primary.context.close();

  const assigned = await openDriver(2, 390);
  await assigned.page.getByText('Yêu cầu nhận chuyến tức thời', { exact: true }).waitFor();
  assert.equal(await assigned.page.locator('.driver-dispatch-offers').count(), 0);
  assert.equal(await assigned.page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
  await assigned.page.getByRole('button', { name: 'Nhận chuyến' }).click();
  await assigned.page.getByRole('button', { name: 'Nhận chuyến' }).waitFor({ state: 'detached' });
  assert.equal(state.acceptCalls, 1);
  await assigned.page.getByRole('button', { name: 'Đã đọc' }).click();
  await assigned.page.getByRole('button', { name: 'Đã đọc' }).waitFor({ state: 'detached' });
  assert.equal(state.read, true);
  await assigned.context.close();
  assert.deepEqual(errors, []);
  assert.deepEqual(retiredRequests, []);
  process.stdout.write('Scheduled auto-start retirement browser smoke passed: 390px assigned trip, direct-assignment accept, inbox; no scheduled dispatch, backup or depot calls.\n');
} finally {
  await browser.close();
}
