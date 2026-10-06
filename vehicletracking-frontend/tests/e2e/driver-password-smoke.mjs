import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import { chromium } from 'playwright';

// UI-only fixtures: these credentials are test values, never operational accounts.
const base = process.env.FRONTEND_URL ?? 'http://localhost:4189';
const output = process.env.DRIVER_PASSWORD_OUTPUT_DIR ?? '/tmp/vehicletracking-driver-password';
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true });
try {
  for (const width of [320, 390, 768, 1440]) {
    // An English browser must still show the application's Vietnamese validation messages.
    const context = await browser.newContext({ viewport: { width, height: 1000 }, locale: 'en-US' });
    let session = true;
    let required = true;
    let businessRequests = 0;
    let changes = 0;
    const errors = [];
    const user = () => ({ accountId: 2, username: 'driver.fixture', role: 'DRIVER', active: true,
      driverId: 1, driverName: 'Tài xế kiểm thử', passwordChangeRequired: required });
    await context.route('**/api/v1/**', async route => {
      const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
      const json = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
      if (path === '/auth/me') return session ? json(user()) : json({ detail: 'Fixture signed out' }, 401);
      if (path === '/auth/csrf') return route.fulfill({ contentType: 'application/json',
        headers: { 'set-cookie': 'XSRF-TOKEN=fixture-csrf; Path=/; SameSite=Lax' }, body: '{}' });
      if (path === '/auth/change-password') {
        changes++;
        assert.equal(route.request().headers()['x-xsrf-token'], 'fixture-csrf');
        const input = route.request().postDataJSON();
        if (input.currentPassword !== 'Initial8Pass') return json({ detail: 'Mật khẩu hiện tại không đúng.' }, 400);
        assert.equal(input.newPassword, 'Personal8Pass');
        assert.equal(input.confirmPassword, 'Personal8Pass');
        required = false;
        session = false;
        return route.fulfill({ status: 204 });
      }
      if (path === '/auth/login') {
        const input = route.request().postDataJSON();
        assert.equal(input.password, 'Personal8Pass');
        session = true;
        return json(user());
      }
      if (path === '/auth/logout') { session = false; return route.fulfill({ status: 204 }); }
      businessRequests++;
      return required ? json({ code: 'PASSWORD_CHANGE_REQUIRED', detail: 'Fixture requires password change' }, 403) : json([]);
    });
    const page = await context.newPage();
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(`${base}/driver/today`);
    await page.waitForURL('**/driver/change-password');
    await page.getByRole('heading', { name: 'Đổi mật khẩu', exact: true }).waitFor();
    assert.equal(businessRequests, 0);
    assert.equal(await page.getByRole('button', { name: 'Quay lại cổng tài xế' }).count(), 0);
    await page.reload();
    await page.getByRole('heading', { name: 'Đổi mật khẩu', exact: true }).waitFor();
    await page.goto(`${base}/driver/trips/7/navigate`);
    await page.waitForURL('**/driver/change-password');
    assert.equal(businessRequests, 0);
    await page.getByRole('heading', { name: 'Đổi mật khẩu', exact: true }).waitFor();
    const size = await page.evaluate(() => ({ scroll: document.documentElement.scrollWidth, viewport: innerWidth }));
    assert.ok(size.scroll <= size.viewport + 1, `Horizontal overflow at ${width}px`);
    await page.screenshot({ path: `${output}/driver-password-${width}.png`, fullPage: true });

    // Real button clicks must reach Vietnamese app validation instead of browser-language popups.
    await page.getByRole('button', { name: 'Đổi mật khẩu', exact: true }).click();
    await page.getByText('Hãy nhập mật khẩu hiện tại.', { exact: true }).waitFor();
    await page.getByLabel('Mật khẩu hiện tại', { exact: true }).fill('Initial8Pass');
    await page.getByLabel('Mật khẩu mới', { exact: true }).fill('123456');
    await page.getByLabel('Xác nhận mật khẩu mới', { exact: true }).fill('123456');
    await page.getByRole('button', { name: 'Đổi mật khẩu', exact: true }).click();
    await page.getByText('Mật khẩu mới phải dài từ 8 đến 100 ký tự.', { exact: true }).waitFor();
    assert.equal(changes, 0);
    assert.equal(businessRequests, 0);
    if (width === 390) await page.screenshot({ path: `${output}/driver-password-validation-390.png`, fullPage: true });
    await page.getByLabel('Mật khẩu mới', { exact: true }).fill('Personal8Pass');
    await page.getByRole('button', { name: 'Đổi mật khẩu', exact: true }).click();
    await page.getByText('Mật khẩu xác nhận không khớp.', { exact: true }).waitFor();
    assert.equal(changes, 0);

    await page.getByLabel('Mật khẩu hiện tại', { exact: true }).fill('Wrong8Pass');
    await page.getByLabel('Mật khẩu mới', { exact: true }).fill('Personal8Pass');
    await page.getByLabel('Xác nhận mật khẩu mới', { exact: true }).fill('Personal8Pass');
    await page.getByRole('button', { name: 'Đổi mật khẩu', exact: true }).click();
    await page.getByText('Mật khẩu hiện tại không đúng.', { exact: true }).waitFor();
    assert.equal(businessRequests, 0);
    await page.getByLabel('Mật khẩu hiện tại', { exact: true }).fill('Initial8Pass');
    await page.getByRole('button', { name: 'Đổi mật khẩu', exact: true }).click();
    await page.waitForURL('**/login');
    assert.equal(changes, 2);
    await page.getByLabel('Tên đăng nhập', { exact: true }).fill('driver.fixture');
    await page.getByLabel('Mật khẩu', { exact: true }).fill('Personal8Pass');
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    await page.waitForURL('**/driver/today');
    await page.locator('.driver-account-trigger').click();
    await page.getByRole('button', { name: 'Đổi mật khẩu', exact: true }).waitFor();
    const portalSize = await page.evaluate(() => ({ scroll: document.documentElement.scrollWidth, viewport: innerWidth }));
    assert.ok(portalSize.scroll <= portalSize.viewport + 1, `Portal header overflow at ${width}px`);
    await page.screenshot({ path: `${output}/driver-portal-password-${width}.png`, fullPage: true });
    await page.getByRole('button', { name: 'Đổi mật khẩu', exact: true }).click();
    await page.waitForURL('**/driver/change-password');
    await page.getByRole('button', { name: 'Quay lại cổng tài xế' }).waitFor();
    assert.deepEqual(errors, []);
    console.log(JSON.stringify({ width, gateBeforeBusinessRequests: true, refresh: true,
      deepLink: true, localizedValidation: true, changeAndRelogin: true, voluntaryEntry: true, horizontalOverflow: false }));
    await context.close();
  }
} finally {
  await browser.close();
}
