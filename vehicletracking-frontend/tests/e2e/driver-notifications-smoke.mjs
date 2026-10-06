import { chromium } from 'playwright';
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { installFixture, stamp, trips } from '../fixtures/api.mjs';

// All API/provider traffic uses fixtures. No operational data is changed.
const base = process.env.FRONTEND_URL ?? 'http://127.0.0.1:5173';
const output = '/tmp/vehicletracking-056-account-browser';
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true });
const errors = [];
const results = [];

async function openDriver(width, count = 50) {
  const state = {
    fail: false,
    accepted: false,
    declined: false,
    readCalls: 0,
    acceptCalls: 0,
    reasons: [],
    inbox: Array.from({ length: count }, (_, i) => ({
      id: i + 1,
      type: i === 0 ? 'DIRECT_ASSIGNMENT_REQUESTED' : 'DIRECT_ASSIGNMENT_ACCEPTED',
      title: i === 0 ? 'Yêu cầu nhận chuyến #8' : `Đã nhận chuyến #${100 + i}`,
      detail:
        i === 0
          ? 'Vui lòng nhận hoặc từ chối yêu cầu nhận chuyến.'
          : 'Bạn đã nhận chuyến được điều phối.',
      tripId: i === 0 ? 8 : 100 + i,
      offerId: null,
      assignmentRequestId: i === 0 ? 'request-8' : `history-${i}`,
      createdAt: stamp,
      readAt: i < 20 ? null : stamp,
    })),
  };
  const context = await browser.newContext({
    viewport: { width, height: width === 1440 ? 1000 : width === 320 ? 740 : 844 },
    locale: 'vi-VN',
    reducedMotion: 'reduce',
  });
  await installFixture(context, base, { role: 'DRIVER', mode: 'data' });
  await context.route('**/api/v1/driver/**', async (route) => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    const json = (value, status = 200) =>
      route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(value) });
    if (path === '/driver/assignment-requests') {
      if (state.fail) return json({ detail: 'Fixture unavailable' }, 503);
      return json(
        count && !state.accepted && !state.declined
          ? [
              {
                requestId: 'request-8',
                tripId: 8,
                routeName: 'Tuyến tức thời',
                vehiclePlate: '51B12345',
                tripCreatedAt: stamp,
                requestedAt: stamp,
              },
            ]
          : [],
      );
    }
    if (path === '/driver/dispatch/inbox')
      return state.fail ? json({ detail: 'Fixture unavailable' }, 503) : json(state.inbox);
    const read = path.match(/^\/driver\/dispatch\/inbox\/(\d+)\/read$/);
    if (read) {
      state.readCalls++;
      const item = state.inbox.find((item) => item.id === Number(read[1]));
      item.readAt = stamp;
      return json(item);
    }
    if (path === '/driver/assignment-requests/request-8/accept') {
      state.acceptCalls++;
      state.accepted = true;
      return json({ tripId: 8, requestId: 'request-8', status: 'ACCEPTED' });
    }
    if (path === '/driver/assignment-requests/request-8/decline') {
      state.declined = true;
      state.reasons.push(route.request().postDataJSON().reason);
      return json({ tripId: 8, requestId: 'request-8', status: 'DECLINED' });
    }
    if (path === '/driver/trips')
      return json(
        state.accepted ? [{ ...trips[0], id: 8, routeName: 'Tuyến tức thời' }, ...trips] : trips,
      );
    return route.fallback();
  });
  const page = await context.newPage();
  page.on('pageerror', (error) => errors.push(error.message));
  await page.goto(`${base}/driver/today`);
  const bell = page.locator('.driver-account-trigger');
  await bell.waitFor();
  await page.locator('.driver-trip-card').first().waitFor();
  if (count) await page.locator('.driver-notifications-badge').waitFor();
  return { context, page, state, bell };
}

async function openNotifications(page) {
  await page.locator('.driver-account-trigger').click();
  await page.locator('.driver-account-notifications').click();
}

try {
  for (const width of [1440, 390, 320]) {
    const { context, page, state, bell } = await openDriver(width);
    assert.equal(await page.locator('.driver-notifications-badge').textContent(), '20');
    assert.equal(await page.locator('.driver-dispatch-workspace').count(), 0);
    const contentTop = await page
      .locator('.driver-summary-grid')
      .evaluate((el) => el.getBoundingClientRect().top);
    assert.equal(await page.locator('.driver-portal-account button').count(), 1);
    await page.screenshot({ path: `${output}/closed-${width}.png` });
    await bell.click();
    const account = page.getByRole('dialog', { name: 'Tài khoản', exact: true });
    await account.waitFor();
    await account.getByRole('button', { name: 'Đổi mật khẩu', exact: true }).waitFor();
    await account.getByRole('button', { name: 'Đăng xuất', exact: true }).waitFor();
    await page.screenshot({ path: `${output}/account-${width}.png` });
    await page.keyboard.press('Escape');
    assert.equal(await bell.evaluate((el) => el === document.activeElement), true);
    await openNotifications(page);
    const panel = page.getByRole('dialog', { name: 'Thông báo', exact: true });
    await panel.waitFor();
    const geometry = await panel.evaluate((el) => {
      const rect = el.getBoundingClientRect();
      const content = el.querySelector('.driver-notifications-content');
      return {
        left: rect.left,
        right: rect.right,
        top: rect.top,
        bottom: rect.bottom,
        viewportWidth: innerWidth,
        viewportHeight: innerHeight,
        scrolls: content.scrollHeight > content.clientHeight,
      };
    });
    assert(
      geometry.left >= 0 &&
        geometry.right <= geometry.viewportWidth &&
        geometry.top >= 0 &&
        geometry.bottom <= geometry.viewportHeight,
    );
    assert(geometry.bottom - geometry.top <= 600);
    assert(geometry.scrolls);
    assert.equal(
      await panel
        .locator('h3')
        .first()
        .evaluate((el) => getComputedStyle(el).color),
      'rgb(15, 23, 42)',
    );
    assert.equal(
      await page.locator('.driver-summary-grid').evaluate((el) => el.getBoundingClientRect().top),
      contentTop,
    );
    assert.equal(
      await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
      true,
    );
    await page.screenshot({ path: `${output}/open-${width}.png` });
    await panel.getByRole('button', { name: 'Quay lại tài khoản', exact: true }).click();
    await page.getByRole('dialog', { name: 'Tài khoản', exact: true }).waitFor();
    assert.equal(
      await page
        .locator('.driver-account-notifications')
        .evaluate((el) => el === document.activeElement),
      true,
    );
    await page.locator('.driver-account-notifications').click();
    await panel.waitFor();
    await panel.getByRole('button', { name: 'Đã đọc', exact: true }).first().click();
    await page.waitForFunction(() =>
      document.querySelector('.driver-dispatch-heading p')?.textContent.includes('19 chưa đọc'),
    );
    assert.equal(state.readCalls, 1);
    assert.equal(state.acceptCalls, 0);
    assert.equal(await page.locator('.driver-notifications-badge').textContent(), '20');
    assert.equal(await panel.getByRole('button', { name: 'Nhận chuyến', exact: true }).count(), 1);
    await panel.locator('.driver-notifications-content').evaluate((el) => {
      el.scrollTop = el.scrollHeight;
    });
    await panel.getByRole('button', { name: 'Đóng thông báo', exact: true }).click();
    await bell.focus();
    await page.keyboard.press('Enter');
    await page.locator('.driver-account-notifications').click();
    await panel.waitFor();
    await page.keyboard.press('Escape');
    await panel.waitFor({ state: 'detached' });
    assert.equal(await bell.evaluate((el) => el === document.activeElement), true);
    await openNotifications(page);
    await page.mouse.click(5, 5);
    await panel.waitFor({ state: 'detached' });
    await page.getByRole('link', { name: 'Lịch chạy', exact: true }).click();
    await openNotifications(page);
    await panel.waitFor();
    await panel.getByRole('button', { name: 'Từ chối', exact: true }).click();
    const confirm = page.locator('.fleet-confirm');
    await confirm.waitFor();
    assert.equal(
      await confirm.evaluate((el) => getComputedStyle(el).backgroundColor),
      'rgb(255, 255, 255)',
    );
    await page.screenshot({ path: `${output}/decline-${width}.png` });
    await page.keyboard.press('Escape');
    await confirm.waitFor({ state: 'detached' });
    assert.equal(await panel.count(), 1);
    assert.equal(state.declined, false);
    await panel.getByRole('button', { name: 'Từ chối', exact: true }).click();
    await confirm.locator('textarea').fill('Không thể nhận chuyến');
    await confirm.getByRole('button', { name: 'Xác nhận từ chối', exact: true }).click();
    await panel
      .getByRole('button', { name: 'Nhận chuyến', exact: true })
      .waitFor({ state: 'detached' });
    assert.deepEqual(state.reasons, ['Không thể nhận chuyến']);
    results.push({ width, geometry, status: 'passed' });
    console.log(
      `PASS ${width}px: 50 notifications scroll inside panel, stable page layout, read/badge, keyboard/outside dismissal, schedule tab and native decline dialog`,
    );
    await context.close();
  }
  const accepted = await openDriver(390);
  await openNotifications(accepted.page);
  await accepted.page.getByRole('button', { name: 'Nhận chuyến', exact: true }).click();
  await accepted.page.locator('.driver-trip-card').filter({ hasText: 'Tuyến tức thời' }).waitFor();
  assert.equal(accepted.state.acceptCalls, 1);
  console.log('PASS accept in bell updates assigned trip list');
  await accepted.context.close();
  const empty = await openDriver(390, 0);
  empty.state.fail = true;
  await openNotifications(empty.page);
  await empty.page.getByRole('button', { name: 'Làm mới thông báo', exact: true }).click();
  await empty.page.locator('.dispatch-error').waitFor();
  empty.state.fail = false;
  await empty.page.getByRole('button', { name: 'Làm mới thông báo', exact: true }).click();
  await empty.page.locator('.dispatch-error').waitFor({ state: 'detached' });
  await empty.page
    .getByText('Chưa có thông báo hoặc yêu cầu nhận chuyến mới.', { exact: true })
    .waitFor();
  console.log('PASS notification load errors and retry to empty state');
  await empty.context.close();
  assert.deepEqual(errors, []);
  await writeFile(`${output}/results.json`, JSON.stringify({ results, errors }, null, 2));
} finally {
  await browser.close();
}
