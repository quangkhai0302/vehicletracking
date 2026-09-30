import { chromium } from 'playwright';
import assert from 'node:assert/strict';

// Isolated UI smoke: all backend responses are fixtures, no real trips are changed.
const base = process.env.FRONTEND_URL ?? 'http://127.0.0.1:4185';
const browser = await chromium.launch({ headless: true });
const errors = [];
const departure = new Date(Date.now() + 10 * 60_000).toISOString();
const cutoff = new Date(Date.now() + 25 * 60_000).toISOString();
const trip = {
  id: 7, vehicleId: 2, vehiclePlateNumber: '51B12345', vehicleType: 'CAR',
  routeId: 3, routeName: 'Tuyến điều phối', status: 'SCHEDULED',
  scheduledDepartureAt: departure, plannedEndAt: cutoff,
  startedAt: null, endedAt: null, createdAt: new Date().toISOString(),
  dispatchMode: 'FIXED_SCHEDULE', scheduleId: 4, scheduleName: 'Lịch thử',
  driver: null, dispatch: { startMode: 'AUTO_IF_READY', state: 'WAITING_READY',
    attentionCode: null, readyAt: null, revision: 1 },
};
const state = { ready: false, busy: false, accepted: false, read: false, readyCalls: 0, busyCalls: 0, acceptCalls: 0 };

async function openDriver(driverId, width) {
  const context = await browser.newContext({ viewport: { width, height: 844 },
    isMobile: width < 700, hasTouch: width < 700 });
  await context.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace('/api/v1', '');
    const json = (value, status = 200) => route.fulfill({ status,
      contentType: 'application/json', body: JSON.stringify(value) });
    if (path === '/auth/me') return json({ accountId: driverId, username: `driver${driverId}`,
      role: 'DRIVER', active: true, driverId, driverName: `Tài xế ${driverId}` });
    if (path === '/auth/csrf') return route.fulfill({ contentType: 'application/json',
      headers: { 'set-cookie': 'XSRF-TOKEN=fixture-csrf; Path=/; SameSite=Lax' }, body: '{}' });
    if (path === '/driver/schedules') return json([]);
    if (path === '/driver/trips') return json(driverId === 1 && !state.busy ? [trip] : []);
    if (path === '/driver/trips/7/dispatch') {
      if (driverId !== 1 || state.busy) return json({ detail: 'Không tìm thấy chuyến.' }, 404);
      return json({ tripId: 7, startMode: 'AUTO_IF_READY',
        state: state.ready ? 'READY' : 'WAITING_READY', attentionCode: null,
        readyAt: state.ready ? new Date().toISOString() : null, cutoffAt: cutoff,
        revision: state.ready ? 2 : 1, canReady: !state.ready, canReportUnavailable: true });
    }
    if (path === '/driver/trips/7/dispatch/ready') {
      state.readyCalls++; state.ready = true;
      return json({ tripId: 7, startMode: 'AUTO_IF_READY', state: 'READY',
        attentionCode: null, readyAt: new Date().toISOString(), cutoffAt: cutoff,
        revision: 2, canReady: false, canReportUnavailable: true });
    }
    if (path === '/driver/trips/7/dispatch/unavailable') {
      state.busyCalls++; state.busy = true;
      return json({ tripId: 7, state: 'SEARCH_WAIT', revision: 3 });
    }
    if (path === '/driver/dispatch/offers') return json(driverId === 2 && !state.accepted ? [{
      offerId: 'offer-7', tripId: 7, routeName: 'Tuyến điều phối',
      vehiclePlate: '51B12345', scheduledDepartureAt: departure, cutoffAt: cutoff,
      expiresAt: cutoff, revision: 4,
    }] : []);
    if (path === '/driver/dispatch/offers/offer-7/accept') {
      state.acceptCalls++; state.accepted = true;
      return json({ tripId: 7, status: 'ACCEPTED', dispatch: {
        tripId: 7, startMode: 'AUTO_IF_READY', state: 'WAITING_READY',
        attentionCode: null, readyAt: null, cutoffAt: cutoff, revision: 5,
        canReady: true, canReportUnavailable: true,
      } });
    }
    if (path === '/driver/dispatch/inbox') return json(driverId === 2 ? [{
      id: 9, type: 'DISPATCH_OFFER', title: 'Lời mời nhận chuyến #7',
      detail: 'Tuyến điều phối', tripId: 7, offerId: 'offer-7',
      createdAt: new Date().toISOString(), readAt: state.read ? new Date().toISOString() : null,
    }] : []);
    if (path === '/driver/dispatch/inbox/9/read') {
      state.read = true;
      return json({ id: 9, title: 'Lời mời nhận chuyến #7', readAt: new Date().toISOString() });
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
  await primary.page.getByRole('button', { name: 'Sẵn sàng' }).click();
  await primary.page.getByText('Đã xác nhận lúc').waitFor();
  assert.equal(state.readyCalls, 1);
  assert.equal(await primary.page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
  await primary.page.getByRole('button', { name: 'Báo bận' }).click();
  const dialog = primary.page.locator('dialog.fleet-confirm[open]');
  await dialog.waitFor();
  await dialog.locator('textarea').fill('Tôi không thể thực hiện chuyến này');
  await dialog.getByRole('button', { name: 'Xác nhận báo bận' }).click();
  await primary.page.getByText('Chưa có chuyến được phân công').waitFor();
  assert.equal(state.busyCalls, 1);
  await primary.context.close();

  const candidate = await openDriver(2, 390);
  await candidate.page.getByText('Lời mời nhận chuyến', { exact: true }).waitFor();
  assert.equal(await candidate.page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
  await candidate.page.getByRole('button', { name: 'Nhận chuyến' }).click();
  await candidate.page.getByRole('button', { name: 'Nhận chuyến' }).waitFor({ state: 'detached' });
  assert.equal(state.acceptCalls, 1);
  await candidate.page.getByRole('button', { name: 'Đã đọc' }).click();
  await candidate.page.getByRole('button', { name: 'Đã đọc' }).waitFor({ state: 'detached' });
  assert.equal(state.read, true);
  await candidate.context.close();
  assert.deepEqual(errors, []);
  process.stdout.write('Scheduled dispatch browser smoke passed: 390px READY, busy, offer accept, inbox.\n');
} finally {
  await browser.close();
}
