import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import { chromium } from 'playwright';
import { driverSnapshot, stamp } from '../unit/fixtures/driverNavigation.ts';

// Fixture-only regression for the missing randomUUID API on the deployed HTTP site.
const base = process.env.UI_BASE_URL || 'http://127.0.0.1:5174';
assert(['127.0.0.1', 'localhost'].includes(new URL(base).hostname), 'Use a local frontend only');
const output = process.env.UI_OUTPUT_DIR || '/tmp/vehicletracking-http-incident-fix';
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true });
try {
  for (const width of [1440, 390]) {
    const context = await browser.newContext({ viewport: { width, height: 950 } });
    try {
      await context.addInitScript(() => Object.defineProperty(Crypto.prototype, 'randomUUID', { value: undefined, configurable: true }));
      const submissions = [], errors = [];
      let saved = false;
      const snapshot = () => {
        const result = driverSnapshot('IN_PROGRESS');
        if (saved) result.simulation.status = 'PAUSED';
        return result;
      };
      await context.route('**/*', async route => {
        const url = new URL(route.request().url());
        if (!url.pathname.startsWith('/api/v1/')) {
          return url.origin === new URL(base).origin ? route.continue() : route.abort();
        }
        const path = url.pathname.replace('/api/v1', '');
        const json = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
        if (path === '/auth/me') return json({ accountId: 2, username: 'fixture-driver', role: 'DRIVER', active: true, passwordChangeRequired: false, driverId: 9, driverName: 'Tài xế thử nghiệm' });
        if (path === '/auth/csrf') return json({});
        if (path === '/driver/trips/7/navigation') return json(snapshot());
        if (path === '/driver/trips/7/simulation/incidents') {
          submissions.push(route.request().postDataJSON());
          if (submissions.length === 1) return json({ detail: 'Lỗi thử nghiệm để kiểm tra gửi lại.' }, 503);
          saved = true;
          return json({ id: 1, tripId: 7, vehicleId: 1, vehiclePlateNumber: '51B-12345', reportedByDriverId: 9,
            reportedByDriverName: 'Tài xế thử nghiệm', attemptNumber: 1, type: 'VEHICLE_BREAKDOWN', severity: 'MAJOR',
            status: 'OPEN', detail: 'Kiểm thử báo sự cố qua HTTP', latitude: 10.77, longitude: 106.7,
            simulatedElapsedSeconds: 0, createdAt: stamp, acknowledgedAt: null, resolvedAt: null, simulation: snapshot().simulation });
        }
        throw new Error(`Unexpected fixture API: ${path}`);
      });
      const page = await context.newPage();
      page.on('pageerror', error => errors.push(error.message));
      await page.goto(`${base}/driver/trips/7/navigate`);
      assert.equal(await page.evaluate(() => typeof crypto.randomUUID), 'undefined');
      await page.getByRole('button', { name: 'Báo cáo sự cố', exact: true }).click();
      const dialog = page.locator('dialog.simulation-incident-dialog');
      await dialog.waitFor();
      await dialog.locator('textarea').fill('Kiểm thử báo sự cố qua HTTP');
      await dialog.getByRole('button', { name: 'Ghi nhận sự cố', exact: true }).click();
      await dialog.locator('.simulation-incident-error').waitFor();
      await page.screenshot({ path: `${output}/incident-retry-${width}.png`, fullPage: true });
      await dialog.getByRole('button', { name: 'Ghi nhận sự cố', exact: true }).click();
      await dialog.waitFor({ state: 'detached' });
      assert.equal(submissions.length, 2);
      assert.match(submissions[0].idempotencyKey, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
      assert.equal(submissions[0].idempotencyKey, submissions[1].idempotencyKey);
      assert.equal(submissions[1].detail, 'Kiểm thử báo sự cố qua HTTP');
      assert.deepEqual(errors, []);
      console.log(`PASS incident dialog, submit and retry without randomUUID at ${width}px`);
    } finally { await context.close(); }
  }
} finally { await browser.close(); }
